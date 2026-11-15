package edu.comp5100.election.security;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.support.HttpTestClient;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.ServerTest;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Security checks over real HTTP against the embedded server: real login, real cookies, real
 * error dispatch. This covers what MockMvc cannot show, such as the rendered error pages.
 */
@ServerTest
@DisplayName("Security over real HTTP")
class HttpSecurityIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @LocalServerPort
    private int port;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;
    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private BallotRepository ballotRepository;

    private UserAccount admin;
    private UserAccount voter;
    private Election election;
    private List<Candidate> candidates;

    @BeforeEach
    void createAccountsAndOpenElection() {
        clock.setInstant(BASE);
        admin = data.admin("http-admin");
        voter = data.voter("http-voter");
        election = data.openElection("HTTP election", BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)),
                "Jordan Lee", "Priya Nair");
        candidates = data.candidatesOf(election);
        data.makeEligible(election, voter);
    }

    private static void assertSafeEnglishErrorPage(HttpResponse<String> response, String expectedTitle) {
        assertThat(response.body()).contains("<html lang=\"en\"").contains(expectedTitle);
        assertThat(response.body()).doesNotContain("Exception", "at edu.comp5100", "org.springframework",
                "Whitelabel", "stackTrace");
    }

    @Test
    @DisplayName("NFR-04: the session cookie is HttpOnly with SameSite, and the session id never appears in a URL")
    void sessionCookieIsProtected() {
        HttpResponse<String> response = new HttpTestClient(port).get("/login");

        String cookie = response.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(cookie).startsWith("JSESSIONID=").contains("HttpOnly").containsIgnoringCase("SameSite=Lax");
        assertThat(response.body()).doesNotContainIgnoringCase("jsessionid");
    }

    @Test
    @DisplayName("FR-01: a real login with the form works and lands on the voter home page")
    void realLoginWorks() {
        HttpResponse<String> home = new HttpTestClient(port).login(voter.getUsername(), TestData.PASSWORD);

        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("My elections").contains(election.getTitle());
    }

    @Test
    @DisplayName("FR-16/NFR-04: a voter who opens an administrator URL gets an English 403 page without internals")
    void forbiddenPageIsSafe() {
        HttpTestClient client = new HttpTestClient(port);
        client.login(voter.getUsername(), TestData.PASSWORD);

        HttpResponse<String> response = client.get("/admin/audit");

        assertThat(response.statusCode()).isEqualTo(403);
        assertSafeEnglishErrorPage(response, "Access denied");
    }

    @Test
    @DisplayName("NFR-04: an unknown address gives an English 404 page without internals")
    void notFoundPageIsSafe() {
        HttpTestClient client = new HttpTestClient(port);
        client.login(voter.getUsername(), TestData.PASSWORD);

        HttpResponse<String> response = client.get("/no/such/page");

        assertThat(response.statusCode()).isEqualTo(404);
        assertSafeEnglishErrorPage(response, "Page not found");
    }

    @Test
    @DisplayName("NFR-04: a real POST without a CSRF token is refused with an English 403 page and stores nothing")
    void postWithoutCsrfTokenIsRefused() {
        HttpTestClient client = new HttpTestClient(port);
        client.login(voter.getUsername(), TestData.PASSWORD);

        HttpResponse<String> response = client.postFormWithoutCsrf(
                "/voter/elections/" + election.getId() + "/vote",
                Map.of("candidateId", String.valueOf(candidates.get(0).getId())));

        assertThat(response.statusCode()).isEqualTo(403);
        assertSafeEnglishErrorPage(response, "Access denied");
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("NFR-04: state-changing addresses do not react to GET requests")
    void stateChangingAddressesRejectGet() {
        HttpTestClient voterClient = new HttpTestClient(port);
        voterClient.login(voter.getUsername(), TestData.PASSWORD);
        HttpTestClient adminClient = new HttpTestClient(port);
        adminClient.login(admin.getUsername(), TestData.PASSWORD);

        HttpResponse<String> voteByGet = voterClient.get("/voter/elections/" + election.getId() + "/vote?candidateId="
                + candidates.get(0).getId());
        HttpResponse<String> closeByGet = adminClient.get("/admin/elections/" + election.getId() + "/close");
        voterClient.get("/logout");
        HttpResponse<String> stillSignedIn = voterClient.get("/voter");

        assertThat(voteByGet.statusCode()).isEqualTo(405);
        assertSafeEnglishErrorPage(voteByGet, "Action not allowed");
        assertThat(closeByGet.statusCode()).isEqualTo(405);
        assertThat(stillSignedIn.statusCode()).isEqualTo(200);
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.OPEN);
    }

    @Test
    @DisplayName("FR-12/FR-13: over real HTTP the code is shown once, is not cacheable and verifies by POST")
    void voteAndVerifyOverRealHttp() {
        HttpTestClient client = new HttpTestClient(port);
        client.login(voter.getUsername(), TestData.PASSWORD);
        client.get("/voter/elections/" + election.getId() + "/ballot");

        HttpResponse<String> redirect = client.postForm("/voter/elections/" + election.getId() + "/vote",
                Map.of("candidateId", String.valueOf(candidates.get(1).getId())));
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location").orElseThrow()).endsWith("/confirmation");

        HttpResponse<String> confirmation = client.follow(redirect);
        assertThat(confirmation.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
        java.util.regex.Matcher code = java.util.regex.Pattern
                .compile("id=\"receipt-code\">([0-9A-Z-]{39})<").matcher(confirmation.body());
        assertThat(code.find()).isTrue();

        HttpResponse<String> reloaded = client.get("/voter/elections/" + election.getId() + "/confirmation");
        assertThat(reloaded.body()).doesNotContain(code.group(1)).contains("cannot be displayed again");

        HttpTestClient anonymous = new HttpTestClient(port);
        anonymous.get("/receipt");
        HttpResponse<String> verified = anonymous.postForm("/receipt", Map.of("code", code.group(1)));
        assertThat(verified.body()).contains("data-status=\"ACCEPTED\"").doesNotContain(code.group(1));
        assertThat(verified.headers().firstValue("Cache-Control").orElseThrow()).isEqualTo("no-store");
    }
}
