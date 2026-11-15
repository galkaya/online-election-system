package edu.comp5100.election.adversarial.unit;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.policy.ActivationProblem;
import edu.comp5100.election.policy.ElectionPhase;
import edu.comp5100.election.policy.VoteRejectionReason;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * AI-assisted adversarial suite, candidate C23 (docs/ai-testing/03-candidate-decisions.md):
 * every rejection must come with a clear English explanation (FR-11, NFR-05). The baseline unit
 * suite never looked at the message texts, which PIT showed as surviving and uncovered mutants.
 */
@Tag("adversarial")
@DisplayName("Adversarial: user-facing rule messages")
class MessageCatalogTest {

    private static void assertEnglishSentence(String message) {
        assertThat(message).isNotBlank().hasSizeGreaterThan(15).endsWith(".");
        assertThat(message).as("plain ASCII English, no mixed-language text").matches("[\\x20-\\x7E]+");
        assertThat(Character.isUpperCase(message.charAt(0))).isTrue();
    }

    @ParameterizedTest
    @EnumSource(VoteRejectionReason.class)
    @DisplayName("FR-11: every vote rejection reason has an English sentence for the voter")
    void everyRejectionReasonHasAMessage(VoteRejectionReason reason) {
        assertEnglishSentence(reason.message());
    }

    @Test
    @DisplayName("FR-11: no two rejection reasons share a message, so the voter can tell them apart")
    void rejectionMessagesAreDistinct() {
        List<String> messages = Arrays.stream(VoteRejectionReason.values()).map(VoteRejectionReason::message).toList();
        assertThat(messages).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("FR-11: the messages name the actual problem")
    void rejectionMessagesNameTheProblem() {
        assertThat(VoteRejectionReason.ALREADY_VOTED.message()).contains("already voted");
        assertThat(VoteRejectionReason.NOT_ELIGIBLE.message()).contains("eligibility");
        assertThat(VoteRejectionReason.VOTING_NOT_STARTED.message()).contains("not started");
        assertThat(VoteRejectionReason.VOTING_ENDED.message()).contains("ended");
        assertThat(VoteRejectionReason.ELECTION_CLOSED.message()).contains("closed");
        assertThat(VoteRejectionReason.INVALID_CANDIDATE.message()).contains("not on the ballot");
        assertThat(VoteRejectionReason.NO_CANDIDATE_SELECTED.message()).contains("Select one candidate");
        assertThat(VoteRejectionReason.ACCOUNT_NOT_PERMITTED.message()).contains("not allowed to vote");
    }

    @ParameterizedTest
    @EnumSource(ActivationProblem.class)
    @DisplayName("FR-06: every activation problem has an English sentence for the administrator")
    void everyActivationProblemHasAMessage(ActivationProblem problem) {
        assertEnglishSentence(problem.message());
    }

    @Test
    @DisplayName("FR-06: the phase labels are present, distinct and English")
    void phaseLabelsAreDistinct() {
        List<String> labels = Arrays.stream(ElectionPhase.values()).map(ElectionPhase::label).toList();
        assertThat(labels).doesNotHaveDuplicates().allMatch(label -> label.matches("[A-Z][a-z ]+"));
        assertThat(ElectionPhase.VOTING.label()).isEqualTo("Voting open");
        assertThat(ElectionPhase.ENDED.label()).isEqualTo("Voting ended");
    }
}
