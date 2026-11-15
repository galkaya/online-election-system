package edu.comp5100.election.adversarial;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.support.HttpTestClient;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.ServerTest;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * AI-assisted adversarial suite, candidates C02, C27 and C28
 * (docs/ai-testing/03-candidate-decisions.md), over real HTTP against the embedded server.
 */
@ServerTest
@Tag("adversarial")
@DisplayName("Adversarial: real HTTP sessions")
class AdversarialHttpIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @LocalServerPort
    private int port;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private Election election;
    private List<Candidate> candidates;
    private UserAccount voter;

    @BeforeEach
    void setUp() {
        clock.setInstant(BASE);
        election = data.openElection("Adversarial HTTP election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Jordan Lee", "Priya Nair", "Marcus Hill");
        candidates = data.candidatesOf(election);
        voter = data.voter("advhttp-voter");
        data.makeEligible(election, voter);
    }

    private String votePath() {
        return "/voter/elections/" + election.getId() + "/vote";
    }

    private HttpTestClient signedInOnTheBallot() {
        HttpTestClient client = new HttpTestClient(port);
        client.login(voter.getUsername(), TestData.PASSWORD);
        assertThat(client.get("/voter/elections/" + election.getId() + "/ballot").statusCode()).isEqualTo(200);
        return client;
    }

    @Test
    @DisplayName("FR-10/NFR-02 (C02): one account voting from eight browser sessions at once gets one vote in total")
    void simultaneousDuplicateOverRealHttpSessions() throws Exception {
        int sessions = 8;
        List<HttpTestClient> clients = new ArrayList<>();
        for (int index = 0; index < sessions; index++) {
            clients.add(signedInOnTheBallot());
        }
        ExecutorService threads = Executors.newFixedThreadPool(sessions);
        try {
            CyclicBarrier startTogether = new CyclicBarrier(sessions);
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int index = 0; index < sessions; index++) {
                HttpTestClient client = clients.get(index);
                String candidateId = String.valueOf(candidates.get(index % candidates.size()).getId());
                futures.add(threads.submit(() -> {
                    startTogether.await(15, TimeUnit.SECONDS);
                    return client.postForm(votePath(), Map.of("candidateId", candidateId));
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS).statusCode());
            }

            assertThat(statuses).filteredOn(status -> status == 302).as("accepted votes").hasSize(1);
            assertThat(statuses).filteredOn(status -> status == 409).as("duplicates").hasSize(sessions - 1);
        } finally {
            threads.shutdownNow();
        }
        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-04 (C27): a CSRF token taken from another session of the same account is refused")
    void csrfTokenOfAnotherSessionIsRefused() {
        HttpTestClient firstSession = signedInOnTheBallot();
        HttpTestClient secondSession = signedInOnTheBallot();
        assertThat(firstSession.csrfToken()).isNotEqualTo(secondSession.csrfToken());

        HttpResponse<String> response = secondSession.postFormWithoutCsrf(votePath(), Map.of(
                "candidateId", String.valueOf(candidates.get(0).getId()),
                "_csrf", firstSession.csrfToken()));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("NFR-04 (C28): the session id changes at sign-in, so a session id planted before login is useless")
    void sessionIdChangesAtLogin() {
        HttpTestClient client = new HttpTestClient(port);
        client.get("/login");
        String beforeLogin = client.sessionId();

        client.login(voter.getUsername(), TestData.PASSWORD);
        String afterLogin = client.sessionId();

        assertThat(beforeLogin).isNotBlank();
        assertThat(afterLogin).isNotBlank().isNotEqualTo(beforeLogin);
    }
}
