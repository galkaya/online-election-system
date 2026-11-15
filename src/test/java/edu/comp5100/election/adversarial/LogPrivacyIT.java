package edu.comp5100.election.adversarial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * AI-assisted adversarial suite, candidate C15 (docs/ai-testing/03-candidate-decisions.md):
 * nothing sensitive reaches the application log (NFR-04). Logging is raised to DEBUG for the
 * application and to INFO for everything else, which is more verbose than the shipped settings.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "logging.level.root=INFO",
        "logging.level.edu.comp5100.election=DEBUG",
        "logging.level.org.springframework.security=DEBUG",
        "logging.level.org.springframework.web=DEBUG"
})
@Tag("adversarial")
@DisplayName("Adversarial: sensitive values in the log")
class LogPrivacyIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("NFR-04 (C15): login, voting, a duplicate attempt and verification log no password, code or choice")
    void sensitiveValuesAreNotLogged(CapturedOutput output) throws Exception {
        clock.setInstant(BASE);
        Election election = data.openElection("Log election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Zoltan Logcandidate", "Other Logcandidate");
        List<Candidate> candidates = data.candidatesOf(election);
        UserAccount voter = data.voter("log-voter");
        data.makeEligible(election, voter);
        String votePath = "/voter/elections/" + election.getId() + "/vote";
        String candidateId = String.valueOf(candidates.get(0).getId());

        mockMvc.perform(formLogin("/login").user(voter.getUsername()).password(TestData.PASSWORD));
        mockMvc.perform(formLogin("/login").user(voter.getUsername()).password("Wrong-Secret-77"));
        MvcResult vote = mockMvc.perform(post(votePath).with(user(voter.getUsername()).roles("VOTER")).with(csrf())
                .param("candidateId", candidateId)).andReturn();
        String code = (String) vote.getFlashMap().get("receiptCode");
        mockMvc.perform(post(votePath).with(user(voter.getUsername()).roles("VOTER")).with(csrf())
                .param("candidateId", candidateId));
        mockMvc.perform(post("/receipt").with(csrf()).param("code", code));
        LoggerFactory.getLogger(LogPrivacyIT.class).info("log-capture-marker");

        String log = output.getAll();
        assertThat(log).as("the capture really sees application log output").contains("log-capture-marker");
        assertThat(code).isNotBlank();
        assertThat(log).doesNotContain(code, ReceiptCodec.normalize(code).orElseThrow());
        assertThat(log).doesNotContain(TestData.PASSWORD, "Wrong-Secret-77");
        assertThat(log).doesNotContain("Zoltan Logcandidate");
        assertThat(log).as("the chosen candidate id is not logged as a request parameter")
                .doesNotContain("candidateId=" + candidateId);
    }
}
