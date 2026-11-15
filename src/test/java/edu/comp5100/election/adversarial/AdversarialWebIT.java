package edu.comp5100.election.adversarial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ParticipationRepository;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * AI-assisted adversarial suite, candidates C08, C17 and C35
 * (docs/ai-testing/03-candidate-decisions.md), through MockMvc with the real security filter
 * chain, services and database.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Tag("adversarial")
@DisplayName("Adversarial: web layer through MockMvc")
class AdversarialWebIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private VotingService votingService;
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
        election = data.openElection("Adversarial web election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Jordan Lee", "Priya Nair");
        candidates = data.candidatesOf(election);
        voter = data.voter("advweb-voter");
        data.makeEligible(election, voter);
    }

    private RequestPostProcessor asVoter() {
        return user(voter.getUsername()).roles("VOTER");
    }

    private static String withoutCsrfToken(String html) {
        return html.replaceAll("name=\"_csrf\"\\s+value=\"[^\"]+\"", "name=\"_csrf\"");
    }

    @ParameterizedTest(name = "candidateId = \"{0}\"")
    @ValueSource(strings = {"-1", "0", "9223372036854775807", "9223372036854775808", "1e3", "1 OR 1=1",
            "1,2", " "})
    @DisplayName("FR-11 (C08): hostile candidate id values are answered with 400 and store nothing")
    void hostileCandidateIdsAreRefused(String candidateId) throws Exception {
        mockMvc.perform(post("/voter/elections/" + election.getId() + "/vote").with(asVoter()).with(csrf())
                        .param("candidateId", candidateId))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(not(containsString("Exception"))));

        assertThat(participationRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @ParameterizedTest(name = "election id = \"{0}\"")
    @ValueSource(strings = {"-1", "0", "9223372036854775807"})
    @DisplayName("FR-11 (C08): election ids that cannot exist are answered with 404")
    void impossibleElectionIdsAreNotFound(String electionId) throws Exception {
        mockMvc.perform(get("/voter/elections/{id}/ballot", electionId).with(asVoter()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("This election does not exist.")));
    }

    @ParameterizedTest(name = "election id = \"{0}\"")
    @ValueSource(strings = {"1 OR 1=1", "9223372036854775808", "1.5", "abc"})
    @DisplayName("FR-11 (C08): election ids that are not numbers are answered with 400 and no internals")
    void malformedElectionIdsAreBadRequests(String electionId) throws Exception {
        mockMvc.perform(get("/voter/elections/{id}/ballot", electionId).with(asVoter()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid request")))
                .andExpect(content().string(not(containsString("NumberFormatException"))));
    }

    @Test
    @DisplayName("FR-13/NFR-01 (C17): the verification answer is identical for different voters, candidates and elections")
    void verificationAnswerDoesNotDependOnTheBallot() throws Exception {
        Election other = data.openElection("Adversarial web other", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        UserAccount otherVoter = data.voter("advweb-other");
        data.makeEligible(other, otherVoter);
        String firstCode = votingService.castVote(voter.getUsername(), election.getId(),
                candidates.get(0).getId()).code();
        String secondCode = votingService.castVote(otherVoter.getUsername(), other.getId(),
                data.candidatesOf(other).get(1).getId()).code();

        String firstAnswer = mockMvc.perform(post("/receipt").with(csrf()).param("code", firstCode))
                .andReturn().getResponse().getContentAsString();
        String secondAnswer = mockMvc.perform(post("/receipt").with(csrf()).param("code", secondCode))
                .andReturn().getResponse().getContentAsString();

        assertThat(firstAnswer).contains("data-status=\"ACCEPTED\"");
        assertThat(withoutCsrfToken(secondAnswer)).isEqualTo(withoutCsrfToken(firstAnswer));
    }

    @Test
    @DisplayName("NFR-04 (C35): markup typed into the code field is never reflected into the page")
    void receiptInputIsNotReflected() throws Exception {
        String payload = "<script>alert('receipt')</script>";

        mockMvc.perform(post("/receipt").with(csrf()).param("code", payload))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-status=\"MALFORMED\"")))
                .andExpect(content().string(not(containsString("alert('receipt')"))))
                .andExpect(content().string(not(containsString("alert(&#39;receipt&#39;)"))));
    }
}
