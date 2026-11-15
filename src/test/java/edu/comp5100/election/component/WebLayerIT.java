package edu.comp5100.election.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import edu.comp5100.election.service.ElectionAdminService;
import edu.comp5100.election.service.UserAdminService;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Web component tests with MockMvc: the real Spring Security filter chain, controllers, Thymeleaf
 * templates, services and H2 database, driven through a mock servlet environment. There is no
 * real HTTP server and no browser here; those are covered by the system suite.
 */
@IntegrationTest
@AutoConfigureMockMvc
@DisplayName("Web layer through MockMvc (security, forms, pages)")
class WebLayerIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;
    @Autowired
    private VotingService votingService;
    @Autowired
    private ElectionAdminService electionAdminService;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;

    private UserAccount admin;
    private UserAccount voter;
    private Election election;
    private List<Candidate> candidates;

    @BeforeEach
    void createAccountsAndOpenElection() {
        clock.setInstant(BASE);
        admin = data.admin("web-admin");
        voter = data.voter("web-voter");
        election = data.openElection("Web election", BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)),
                "Jordan Lee", "Priya Nair", "Marcus Hill");
        candidates = data.candidatesOf(election);
        data.makeEligible(election, voter);
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getUsername()).roles("ADMIN");
    }

    private RequestPostProcessor asVoter() {
        return user(voter.getUsername()).roles("VOTER");
    }

    private String url(String suffix) {
        return "/voter/elections/" + election.getId() + suffix;
    }

    // ----- Login and logout (FR-01) -----

    @Test
    @DisplayName("FR-01/NFR-05: the sign-in page is public, English and declares lang=\"en\"")
    void loginPageIsPublicAndEnglish() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("Username")))
                .andExpect(content().string(containsString("Password")));
    }

    @Test
    @DisplayName("FR-01: anonymous users are sent to the sign-in page")
    void anonymousUsersAreRedirectedToLogin() throws Exception {
        for (String path : List.of("/", "/voter", "/admin", "/admin/voters", "/admin/audit",
                "/elections/" + election.getId() + "/results", url("/ballot"))) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }
    }

    @Test
    @DisplayName("FR-01: valid credentials sign in with the stored role")
    void validCredentialsSignIn() throws Exception {
        mockMvc.perform(formLogin("/login").user(admin.getUsername()).password(TestData.PASSWORD))
                .andExpect(authenticated().withUsername(admin.getUsername()).withRoles("ADMIN"))
                .andExpect(redirectedUrl("/"));
        mockMvc.perform(formLogin("/login").user(voter.getUsername()).password(TestData.PASSWORD))
                .andExpect(authenticated().withRoles("VOTER"));
    }

    @Test
    @DisplayName("FR-01: a wrong password, an unknown user and a disabled account are all refused the same way")
    void invalidLoginsAreRefused() throws Exception {
        UserAccount disabled = data.voter("web-disabled");
        userAdminService.setVoterEnabled(admin.getUsername(), disabled.getId(), false);

        mockMvc.perform(formLogin("/login").user(voter.getUsername()).password("wrong-password"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(formLogin("/login").user("nobody").password(TestData.PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(formLogin("/login").user(disabled.getUsername()).password(TestData.PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(content().string(containsString("The username or password is incorrect")));
    }

    @Test
    @DisplayName("FR-01: the start page sends each role to its own area")
    void homeRedirectsByRole() throws Exception {
        mockMvc.perform(get("/").with(asAdmin())).andExpect(redirectedUrl("/admin"));
        mockMvc.perform(get("/").with(asVoter())).andExpect(redirectedUrl("/voter"));
    }

    @Test
    @DisplayName("FR-01/NFR-04: signing out needs a POST with a CSRF token")
    void logoutNeedsPostWithCsrf() throws Exception {
        mockMvc.perform(post("/logout").with(asVoter()).with(csrf()))
                .andExpect(redirectedUrl("/login?logout"));
        mockMvc.perform(post("/logout").with(asVoter())).andExpect(status().isForbidden());
    }

    // ----- Role boundaries (FR-16) -----

    @Test
    @DisplayName("FR-16: a voter gets 403 on every administrator page")
    void voterCannotOpenAdminPages() throws Exception {
        for (String path : List.of("/admin", "/admin/voters", "/admin/audit", "/admin/elections/new",
                "/admin/elections/" + election.getId())) {
            mockMvc.perform(get(path).with(asVoter())).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("FR-16: a voter cannot perform administrator actions, even with a valid CSRF token")
    void voterCannotPerformAdminActions() throws Exception {
        long electionsBefore = electionRepository.count();
        long usersBefore = userAccountRepository.count();

        mockMvc.perform(post("/admin/elections").with(asVoter()).with(csrf())
                        .param("title", "Hijacked").param("startsAt", "2026-11-19 09:00")
                        .param("endsAt", "2026-11-20 09:00"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/elections/" + election.getId() + "/close").with(asVoter()).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/voters").with(asVoter()).with(csrf())
                        .param("username", "sock.puppet").param("displayName", "Sock").param("password", "Secret-123"))
                .andExpect(status().isForbidden());

        assertThat(electionRepository.count()).isEqualTo(electionsBefore);
        assertThat(userAccountRepository.count()).isEqualTo(usersBefore);
        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.OPEN);
    }

    @Test
    @DisplayName("FR-16: an administrator gets 403 on voter pages and cannot submit a vote")
    void adminCannotVote() throws Exception {
        mockMvc.perform(get("/voter").with(asAdmin())).andExpect(status().isForbidden());
        mockMvc.perform(post(url("/vote")).with(asAdmin()).with(csrf())
                        .param("candidateId", String.valueOf(candidates.get(0).getId())))
                .andExpect(status().isForbidden());

        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    // ----- CSRF (NFR-04) -----

    @Test
    @DisplayName("NFR-04: a vote without a CSRF token is refused and nothing is stored")
    void voteWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post(url("/vote")).with(asVoter())
                        .param("candidateId", String.valueOf(candidates.get(0).getId())))
                .andExpect(status().isForbidden());

        assertThat(participationRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("NFR-04: a vote with an invalid CSRF token is refused and nothing is stored")
    void voteWithInvalidCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post(url("/vote")).with(asVoter()).with(csrf().useInvalidToken())
                        .param("candidateId", String.valueOf(candidates.get(0).getId())))
                .andExpect(status().isForbidden());

        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("NFR-04: administrator actions without a CSRF token are refused")
    void adminActionWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/admin/elections/" + election.getId() + "/close").with(asAdmin()))
                .andExpect(status().isForbidden());

        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.OPEN);
    }

    @Test
    @DisplayName("NFR-04: forms carry a CSRF token field")
    void formsContainCsrfToken() throws Exception {
        mockMvc.perform(get(url("/ballot")).with(asVoter()))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/login")).andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    // ----- Administrator forms (FR-03) -----

    @Test
    @DisplayName("FR-03/NFR-05: an invalid election form is redisplayed with English field messages")
    void invalidElectionFormShowsEnglishMessages() throws Exception {
        long before = electionRepository.count();

        mockMvc.perform(post("/admin/elections").with(asAdmin()).with(csrf())
                        .param("title", "").param("description", "x").param("startsAt", "tomorrow")
                        .param("endsAt", ""))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Enter a title.")))
                .andExpect(content().string(containsString("Use the format YYYY-MM-DD HH:MM")))
                .andExpect(content().string(containsString("Enter an end time.")))
                .andExpect(content().string(containsString("The election was not saved.")));

        assertThat(electionRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-03: an end time before the start time and an impossible date are refused")
    void windowErrorsAreReported() throws Exception {
        mockMvc.perform(post("/admin/elections").with(asAdmin()).with(csrf())
                        .param("title", "Backwards").param("startsAt", "2026-11-20 09:00")
                        .param("endsAt", "2026-11-19 09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("The end time must be after the start time.")));
        mockMvc.perform(post("/admin/elections").with(asAdmin()).with(csrf())
                        .param("title", "Impossible").param("startsAt", "2026-02-30 09:00")
                        .param("endsAt", "2026-03-01 09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("This is not a real date and time.")));
    }

    @Test
    @DisplayName("FR-03/NFR-07: a valid form creates a draft; times are read in the display zone and stored in UTC")
    void validElectionFormCreatesDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/admin/elections").with(asAdmin()).with(csrf())
                        .param("title", "Created through the form").param("description", "Form test")
                        .param("startsAt", "2026-11-19 09:00").param("endsAt", "2026-11-20 17:30"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/admin/elections/*"))
                .andExpect(flash().attributeExists("success"))
                .andReturn();

        String location = result.getResponse().getRedirectedUrl();
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
        Election created = electionRepository.findById(id).orElseThrow();
        assertThat(created.getStatus()).isEqualTo(ElectionStatus.DRAFT);
        assertThat(created.getStartsAt()).isEqualTo(Instant.parse("2026-11-19T15:00:00Z"));
        assertThat(created.getEndsAt()).isEqualTo(Instant.parse("2026-11-20T23:30:00Z"));
    }

    @Test
    @DisplayName("FR-06: a refused administrator action redirects back with an English error message")
    void refusedAdminActionShowsMessage() throws Exception {
        Election draft = data.draftElection("One candidate", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Only One");

        mockMvc.perform(post("/admin/elections/" + draft.getId() + "/activate").with(asAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/admin/elections/" + draft.getId()))
                .andExpect(flash().attribute("error", containsString("At least two candidates")));

        assertThat(electionRepository.findById(draft.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.DRAFT);
    }

    @Test
    @DisplayName("FR-02 to FR-06, FR-15: every administrator page renders in English")
    void adminPagesRender() throws Exception {
        mockMvc.perform(get("/admin").with(asAdmin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("New election")));
        mockMvc.perform(get("/admin/elections/new").with(asAdmin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Voting opens")))
                .andExpect(content().string(containsString("America/Chicago")));
        mockMvc.perform(get("/admin/elections/" + election.getId()).with(asAdmin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Eligible voters")))
                .andExpect(content().string(containsString("Close election")))
                .andExpect(content().string(containsString("Nov 19, 2026, 8:00 AM CST")));
        mockMvc.perform(get("/admin/voters").with(asAdmin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Create a voter account")));
        mockMvc.perform(get("/admin/audit").with(asAdmin())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Event log")));
    }

    // ----- Voter pages (FR-08 to FR-12) -----

    @Test
    @DisplayName("FR-08: the voter home page shows the election and the participation status")
    void voterDashboardRenders() throws Exception {
        mockMvc.perform(get("/voter").with(asVoter()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(election.getTitle())))
                .andExpect(content().string(containsString("You have not voted yet")))
                .andExpect(content().string(containsString("Vote now")));
    }

    @Test
    @DisplayName("FR-09: the ballot lists the candidates; a missing or foreign selection is refused with a message")
    void ballotAndSelectionValidation() throws Exception {
        Election other = data.openElection("Other", BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)),
                "Yes", "No");
        Long foreignCandidate = data.candidatesOf(other).get(0).getId();

        mockMvc.perform(get(url("/ballot")).with(asVoter()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Jordan Lee")))
                .andExpect(content().string(containsString("Choose one candidate")));
        mockMvc.perform(post(url("/review")).with(asVoter()).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Select one candidate before continuing.")));
        mockMvc.perform(post(url("/review")).with(asVoter()).with(csrf())
                        .param("candidateId", String.valueOf(foreignCandidate)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("not on the ballot")));
        mockMvc.perform(post(url("/review")).with(asVoter()).with(csrf())
                        .param("candidateId", String.valueOf(candidates.get(1).getId())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Review your choice")))
                .andExpect(content().string(containsString("Priya Nair")));

        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("FR-12: voting uses Post/Redirect/Get and the confirmation code is displayed exactly once")
    void voteUsesPostRedirectGetAndShowsCodeOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MvcResult vote = mockMvc.perform(post(url("/vote")).session(session).with(asVoter()).with(csrf())
                        .param("candidateId", String.valueOf(candidates.get(0).getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(url("/confirmation")))
                .andExpect(flash().attributeExists("receiptCode"))
                .andReturn();
        String code = (String) vote.getFlashMap().get("receiptCode");
        assertThat(vote.getResponse().getRedirectedUrl()).doesNotContain(code);

        mockMvc.perform(get(url("/confirmation")).session(session).with(asVoter()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your vote has been recorded")))
                .andExpect(content().string(containsString(code)));
        mockMvc.perform(get(url("/confirmation")).session(session).with(asVoter()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cannot be displayed again")))
                .andExpect(content().string(not(containsString(code))));

        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-10/FR-11: a second vote over HTTP gets 409 and an English explanation")
    void secondVoteOverHttpIsRejected() throws Exception {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        mockMvc.perform(post(url("/vote")).with(asVoter()).with(csrf())
                        .param("candidateId", String.valueOf(candidates.get(1).getId())))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Vote not accepted")))
                .andExpect(content().string(containsString("You have already voted in this election.")));
        mockMvc.perform(get(url("/ballot")).with(asVoter())).andExpect(status().isConflict());

        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-08/NFR-01: after voting, the voter pages show participation but never the chosen candidate")
    void votedStateNeverShowsTheChoice() throws Exception {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(2).getId());

        mockMvc.perform(get("/voter").with(asVoter()))
                .andExpect(content().string(containsString("You have voted")))
                .andExpect(content().string(not(containsString("Marcus Hill"))))
                .andExpect(content().string(not(containsString("Vote now"))));
        mockMvc.perform(get(url("/confirmation")).with(asVoter()))
                .andExpect(content().string(not(containsString("Marcus Hill"))));
    }

    @Test
    @DisplayName("FR-11: an invalid or unknown election id gives a plain English error page")
    void invalidIdsGiveEnglishErrorPages() throws Exception {
        mockMvc.perform(get("/voter/elections/abc/ballot").with(asVoter()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid request")))
                .andExpect(content().string(not(containsString("Exception"))));
        mockMvc.perform(get("/voter/elections/9999999/ballot").with(asVoter()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This election does not exist.")));
        mockMvc.perform(get("/admin/elections/9999999").with(asAdmin()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Page not found")));
        mockMvc.perform(post(url("/vote")).with(asVoter()).with(csrf()).param("candidateId", "not-a-number"))
                .andExpect(status().isBadRequest());
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("FR-07/FR-11: early, expired and closed elections are refused over HTTP with their own messages")
    void timeAndStateRejectionsOverHttp() throws Exception {
        String candidateId = String.valueOf(candidates.get(0).getId());

        clock.setInstant(BASE.minus(Duration.ofHours(2)));
        mockMvc.perform(post(url("/vote")).with(asVoter()).with(csrf()).param("candidateId", candidateId))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("has not started yet")));

        clock.setInstant(BASE.plus(Duration.ofHours(1)));
        mockMvc.perform(post(url("/vote")).with(asVoter()).with(csrf()).param("candidateId", candidateId))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("voting period for this election has ended")));

        clock.setInstant(BASE);
        electionAdminService.close(admin.getUsername(), election.getId());
        mockMvc.perform(post(url("/vote")).with(asVoter()).with(csrf()).param("candidateId", candidateId))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("closed by the administrator")));

        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    // ----- Receipt verification (FR-13) -----

    @Test
    @DisplayName("FR-13: a code is verified by POST only; the answer is accepted, not found or malformed")
    void receiptVerification() throws Exception {
        String code = votingService.castVote(voter.getUsername(), election.getId(), candidates.get(1).getId()).code();

        mockMvc.perform(post("/receipt").with(csrf()).param("code", code))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("data-status=\"ACCEPTED\"")))
                .andExpect(content().string(not(containsString(code))))
                .andExpect(content().string(not(containsString("Priya Nair"))))
                .andExpect(content().string(not(containsString(voter.getUsername()))));
        mockMvc.perform(post("/receipt").with(csrf()).param("code", "0000-0000-0000-0000-0000-0000-0000-0000"))
                .andExpect(content().string(containsString("data-status=\"NOT_FOUND\"")));
        mockMvc.perform(post("/receipt").with(csrf()).param("code", "hello"))
                .andExpect(content().string(containsString("data-status=\"MALFORMED\"")));
        mockMvc.perform(post("/receipt").with(csrf()))
                .andExpect(content().string(containsString("data-status=\"MALFORMED\"")));
        // A code in the query string of a GET is ignored: the page only shows the empty form.
        mockMvc.perform(get("/receipt").param("code", code))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("data-status"))));
        mockMvc.perform(post("/receipt").param("code", code)).andExpect(status().isForbidden());
    }

    // ----- Results (FR-14) -----

    @Test
    @DisplayName("FR-14: a direct request for results during voting gets 403 and no totals")
    void resultsAreNotAvailableDuringVoting() throws Exception {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        for (RequestPostProcessor viewer : List.of(asAdmin(), asVoter())) {
            mockMvc.perform(get("/elections/" + election.getId() + "/results").with(viewer))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(containsString("Results are not available yet")))
                    .andExpect(content().string(not(containsString("results-table"))))
                    .andExpect(content().string(not(containsString("Jordan Lee"))));
        }
    }

    @Test
    @DisplayName("FR-14: after closing, the results page shows every candidate, the total and the winner")
    void resultsAfterClosing() throws Exception {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());
        electionAdminService.close(admin.getUsername(), election.getId());

        mockMvc.perform(get("/elections/" + election.getId() + "/results").with(asVoter()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-outcome=\"WINNER\"")))
                .andExpect(content().string(containsString("Jordan Lee")))
                .andExpect(content().string(containsString("Marcus Hill")))
                .andExpect(content().string(containsString("Total accepted votes")))
                .andExpect(content().string(containsString("100.0%")));
    }
}
