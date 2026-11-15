package edu.comp5100.election.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.repository.AuditEventRepository;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Security and privacy regression tests through MockMvc with the real security filter chain,
 * services and database. Each test checks the HTTP answer and that the stored data is intact.
 * This is a limited regression pack, not a penetration test.
 */
@IntegrationTest
@AutoConfigureMockMvc
@DisplayName("Security and privacy regression")
class SecurityRegressionIT {

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
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserAccount admin;
    private UserAccount voter;
    private Election election;
    private List<Candidate> candidates;

    @BeforeEach
    void createAccountsAndOpenElection() {
        clock.setInstant(BASE);
        admin = data.admin("sec-admin");
        voter = data.voter("sec-voter");
        election = data.openElection("Security election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Zed Quuxalpha", "Yan Quuxbeta", "Xia Quuxgamma");
        candidates = data.candidatesOf(election);
        data.makeEligible(election, voter);
    }

    private static RequestPostProcessor as(UserAccount account) {
        return user(account.getUsername()).roles(account.getRole().name());
    }

    private String voteUrl(Election target) {
        return "/voter/elections/" + target.getId() + "/vote";
    }

    private String candidateId(int index) {
        return String.valueOf(candidates.get(index).getId());
    }

    @Test
    @DisplayName("FR-16: request parameters cannot name another voter: the vote always belongs to the signed-in account")
    void requestParametersCannotChangeTheActingVoter() throws Exception {
        UserAccount victim = data.voter("sec-victim");
        data.makeEligible(election, victim);

        mockMvc.perform(post(voteUrl(election)).with(as(voter)).with(csrf())
                        .param("candidateId", candidateId(0))
                        .param("username", victim.getUsername())
                        .param("voterId", String.valueOf(victim.getId()))
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection());

        assertThat(participationRepository.existsByElectionIdAndVoterId(election.getId(), voter.getId())).isTrue();
        assertThat(participationRepository.existsByElectionIdAndVoterId(election.getId(), victim.getId())).isFalse();
        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-16: a role request parameter does not open administrator functions")
    void roleParameterDoesNotGrantAdminRights() throws Exception {
        mockMvc.perform(get("/admin").param("role", "ADMIN").with(as(voter))).andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/elections/" + election.getId() + "/close").param("role", "ADMIN")
                        .with(as(voter)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/admin/voters/" + voter.getId() + "/disable").with(as(voter)).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.OPEN);
    }

    @Test
    @DisplayName("FR-11: a candidate id of another election is refused, stores nothing, and does not block a later valid vote")
    void candidateOfAnotherElectionIsRefused() throws Exception {
        Election other = data.openElection("Other election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        data.makeEligible(other, voter);
        String foreignCandidate = String.valueOf(data.candidatesOf(other).get(0).getId());

        mockMvc.perform(post(voteUrl(election)).with(as(voter)).with(csrf()).param("candidateId", foreignCandidate))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("not on the ballot")));

        assertThat(participationRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(other.getId())).isZero();

        mockMvc.perform(post(voteUrl(election)).with(as(voter)).with(csrf()).param("candidateId", candidateId(1)))
                .andExpect(status().is3xxRedirection());
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(1).getId());
    }

    @Test
    @DisplayName("FR-17: a session opened before the account was disabled is ended and cannot vote")
    void disabledAccountWithOpenSessionCannotVote() throws Exception {
        MvcResult login = mockMvc.perform(formLogin("/login").user(voter.getUsername()).password(TestData.PASSWORD))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mockMvc.perform(get("/voter").session(session)).andExpect(status().isOk());

        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), false);

        mockMvc.perform(post(voteUrl(election)).session(session).with(csrf()).param("candidateId", candidateId(0)))
                .andExpect(redirectedUrl("/login?disabled"));
        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/voter").session(session))
                .andExpect(unauthenticated())
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/login").param("disabled", ""))
                .andExpect(content().string(containsString("This account is no longer active")));

        assertThat(participationRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("FR-05/FR-11: a voter who is not eligible gets 403 for the ballot and for a direct vote")
    void ineligibleVoterIsRefused() throws Exception {
        UserAccount outsider = data.voter("sec-outsider");

        mockMvc.perform(get("/voter/elections/" + election.getId() + "/ballot").with(as(outsider)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("not on the eligibility list")))
                .andExpect(content().string(not(containsString("Zed Quuxalpha"))));
        mockMvc.perform(post(voteUrl(election)).with(as(outsider)).with(csrf()).param("candidateId", candidateId(0)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/voter").with(as(outsider)))
                .andExpect(content().string(containsString("No elections available")));

        assertThat(participationRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("NFR-04: HTML and script in a candidate name is shown as text on every page, never executed")
    void scriptInCandidateNameIsEscaped() throws Exception {
        String payload = "<script>alert('xss')</script>";
        Election draft = data.draftElection("Escaping election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Plain Candidate");
        data.makeEligible(draft, voter);

        mockMvc.perform(post("/admin/elections/" + draft.getId() + "/candidates").with(as(admin)).with(csrf())
                        .param("name", payload).param("biography", "<b onmouseover=alert(1)>bold</b>"))
                .andExpect(status().is3xxRedirection());
        electionAdminService.activate(admin.getUsername(), draft.getId());

        mockMvc.perform(get("/admin/elections/" + draft.getId()).with(as(admin)))
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString(payload))))
                .andExpect(content().string(not(containsString("<b onmouseover"))));
        mockMvc.perform(get("/voter/elections/" + draft.getId() + "/ballot").with(as(voter)))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(payload))));
        mockMvc.perform(get("/admin/audit").with(as(admin)))
                .andExpect(content().string(not(containsString(payload))));

        electionAdminService.close(admin.getUsername(), draft.getId());
        mockMvc.perform(get("/elections/" + draft.getId() + "/results").with(as(admin)))
                .andExpect(content().string(containsString("&lt;script&gt;")))
                .andExpect(content().string(not(containsString(payload))));
    }

    @Test
    @DisplayName("NFR-04: HTML in an election title and a voter display name is escaped")
    void scriptInTitleAndDisplayNameIsEscaped() throws Exception {
        String payload = "<img src=x onerror=alert(1)>";
        userAdminService.createVoter(admin.getUsername(), "sec.html.voter", payload, "Secret-Pass-1");
        Election titled = electionAdminService.createElection(admin.getUsername(), payload, payload,
                BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)));

        mockMvc.perform(get("/admin/voters").with(as(admin)))
                .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(content().string(not(containsString(payload))));
        mockMvc.perform(get("/admin").with(as(admin)))
                .andExpect(content().string(not(containsString(payload))));
        mockMvc.perform(get("/admin/elections/" + titled.getId()).with(as(admin)))
                .andExpect(content().string(not(containsString(payload))));
    }

    @Test
    @DisplayName("NFR-04: SQL-shaped text is stored and shown as plain text and changes nothing else")
    void sqlShapedInputIsHarmless() throws Exception {
        String payload = "Robert'); DROP TABLE ballot; --";
        long ballotsBefore = ballotRepository.count();
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        mockMvc.perform(post("/admin/elections").with(as(admin)).with(csrf())
                        .param("title", payload).param("description", "' OR '1'='1")
                        .param("startsAt", "2026-11-19 09:00").param("endsAt", "2026-11-20 09:00"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(formLogin("/login").user("' OR '1'='1").password("' OR '1'='1"))
                .andExpect(unauthenticated());
        mockMvc.perform(formLogin("/login").user(voter.getUsername() + "' --").password("x"))
                .andExpect(unauthenticated());
        mockMvc.perform(post("/receipt").with(csrf()).param("code", "' OR '1'='1"))
                .andExpect(content().string(containsString("data-status=\"MALFORMED\"")));

        assertThat(electionRepository.findAll()).anyMatch(stored -> stored.getTitle().equals(payload));
        assertThat(ballotRepository.count()).isEqualTo(ballotsBefore + 1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from ballot", Long.class)).isEqualTo(ballotsBefore + 1);
    }

    @Test
    @DisplayName("NFR-01: the confirmation code appears on the confirmation page only: not on other pages, not in the database")
    void receiptCodeDoesNotLeak() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult vote = mockMvc.perform(post(voteUrl(election)).session(session).with(as(voter)).with(csrf())
                        .param("candidateId", candidateId(2)))
                .andReturn();
        String code = (String) vote.getFlashMap().get("receiptCode");
        String canonical = ReceiptCodec.normalize(code).orElseThrow();
        mockMvc.perform(get("/voter/elections/" + election.getId() + "/confirmation").session(session)
                .with(as(voter))).andExpect(content().string(containsString(code)));

        for (String adminPage : List.of("/admin", "/admin/audit", "/admin/voters",
                "/admin/elections/" + election.getId())) {
            mockMvc.perform(get(adminPage).with(as(admin)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(code))))
                    .andExpect(content().string(not(containsString(canonical))));
        }
        mockMvc.perform(get("/voter").with(as(voter)))
                .andExpect(content().string(not(containsString(code))));
        electionAdminService.close(admin.getUsername(), election.getId());
        mockMvc.perform(get("/elections/" + election.getId() + "/results").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(code))));

        assertThat(jdbcTemplate.queryForList("select receipt_hash from ballot", String.class))
                .doesNotContain(code, canonical)
                .contains(ReceiptCodec.hash(canonical));
        assertThat(jdbcTemplate.queryForList("select detail from audit_event", String.class))
                .noneMatch(detail -> detail.contains(code) || detail.contains(canonical));
    }

    @Test
    @DisplayName("FR-15/NFR-01: the audit page lists rejected attempts with the account and reason, never with a choice")
    void auditShowsRejectionsWithoutChoices() throws Exception {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(2).getId());
        mockMvc.perform(post(voteUrl(election)).with(as(voter)).with(csrf()).param("candidateId", candidateId(1)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/admin/audit").with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(voter.getUsername())))
                .andExpect(content().string(containsString("Vote attempt rejected")))
                .andExpect(content().string(containsString("Reason: ALREADY_VOTED (vote submission)")))
                .andExpect(content().string(not(containsString("Quuxalpha"))))
                .andExpect(content().string(not(containsString("Quuxbeta"))))
                .andExpect(content().string(not(containsString("Quuxgamma"))));

        assertThat(auditEventRepository.findAll().stream()
                .filter(event -> election.getId().equals(event.getElectionId())))
                .allMatch(event -> event.getEventType() == AuditEventType.VOTE_REJECTED)
                .hasSize(1);
    }

    @Test
    @DisplayName("FR-14: while voting is open, neither the results URL nor the administrator pages reveal totals")
    void noInterimResultsAnywhere() throws Exception {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        mockMvc.perform(get("/elections/" + election.getId() + "/results").with(as(admin)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Quuxalpha"))));
        mockMvc.perform(get("/elections/" + election.getId() + "/results").with(as(voter)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/elections/" + election.getId()).with(as(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("results-table"))))
                .andExpect(content().string(not(containsString("View results"))))
                .andExpect(content().string(not(containsString("Winner"))));
        mockMvc.perform(get("/admin/audit").with(as(admin)))
                .andExpect(content().string(not(containsString("results-table"))))
                .andExpect(content().string(not(containsString("Quuxalpha"))));
    }

    @Test
    @DisplayName("FR-14: a voter cannot read the results of an election they were never part of")
    void resultsOfUnrelatedElectionAreHidden() throws Exception {
        UserAccount stranger = data.voter("sec-stranger");
        electionAdminService.close(admin.getUsername(), election.getId());

        mockMvc.perform(get("/elections/" + election.getId() + "/results").with(as(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Quuxalpha"))));
    }

    @Test
    @DisplayName("NFR-04: responses carry no-store caching and the standard protective headers")
    void protectiveHeadersArePresent() throws Exception {
        mockMvc.perform(get("/voter").with(as(voter)))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
