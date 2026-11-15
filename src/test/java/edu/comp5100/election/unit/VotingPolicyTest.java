package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.policy.VoteContext;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.policy.VotingPolicy;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for the vote acceptance rules (FR-07, FR-10, FR-11, FR-16, FR-17). */
@DisplayName("Voting policy")
class VotingPolicyTest {

    private static final Instant START = Instant.parse("2026-11-19T15:00:00Z");
    private static final Instant END = Instant.parse("2026-11-20T15:00:00Z");
    private static final Instant DURING = Instant.parse("2026-11-19T18:00:00Z");

    /** A context in which every rule is satisfied; each test changes one or two facts. */
    private static final class ContextBuilder {
        private boolean voterMayVote = true;
        private boolean eligible = true;
        private ElectionStatus status = ElectionStatus.OPEN;
        private Instant now = DURING;
        private boolean alreadyVoted = false;
        private boolean candidateSelected = true;
        private boolean candidateOnBallot = true;

        VoteContext build() {
            return new VoteContext(voterMayVote, eligible, status, START, END, now, alreadyVoted, candidateSelected,
                    candidateOnBallot);
        }
    }

    private static ContextBuilder valid() {
        return new ContextBuilder();
    }

    @Test
    @DisplayName("FR-09: an eligible, enabled voter with a valid candidate inside the window is accepted")
    void validVoteIsAccepted() {
        assertThat(VotingPolicy.evaluate(valid().build())).isEmpty();
        assertThat(VotingPolicy.evaluateAccess(valid().build())).isEmpty();
    }

    @Test
    @DisplayName("FR-16/FR-17: a disabled, missing or non-voter account is refused")
    void accountThatMayNotVoteIsRefused() {
        ContextBuilder context = valid();
        context.voterMayVote = false;
        assertThat(VotingPolicy.evaluate(context.build())).contains(VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
    }

    @Test
    @DisplayName("FR-05: a voter without eligibility for this election is refused")
    void ineligibleVoterIsRefused() {
        ContextBuilder context = valid();
        context.eligible = false;
        assertThat(VotingPolicy.evaluate(context.build())).contains(VoteRejectionReason.NOT_ELIGIBLE);
    }

    @ParameterizedTest(name = "status {0} at {1} -> {2}")
    @CsvSource({
            "DRAFT,  2026-11-19T18:00:00Z, ELECTION_NOT_OPEN",
            "OPEN,   2026-11-19T14:59:59Z, VOTING_NOT_STARTED",
            "OPEN,   2026-11-20T15:00:00Z, VOTING_ENDED",
            "OPEN,   2026-11-20T15:00:01Z, VOTING_ENDED",
            "CLOSED, 2026-11-19T18:00:00Z, ELECTION_CLOSED",
            "CLOSED, 2026-11-21T00:00:00Z, ELECTION_CLOSED"
    })
    @DisplayName("FR-07/FR-11: early, late, draft and closed elections refuse the vote with a specific reason")
    void phaseRejections(ElectionStatus status, Instant now, VoteRejectionReason expected) {
        ContextBuilder context = valid();
        context.status = status;
        context.now = now;
        assertThat(VotingPolicy.evaluate(context.build())).contains(expected);
    }

    @Test
    @DisplayName("FR-07: a vote exactly at the start is accepted, one just before the end too")
    void windowEdgesThatAccept() {
        ContextBuilder atStart = valid();
        atStart.now = START;
        assertThat(VotingPolicy.evaluate(atStart.build())).isEmpty();

        ContextBuilder beforeEnd = valid();
        beforeEnd.now = END.minusNanos(1);
        assertThat(VotingPolicy.evaluate(beforeEnd.build())).isEmpty();
    }

    @Test
    @DisplayName("FR-10: a voter who already voted is refused")
    void duplicateVoteIsRefused() {
        ContextBuilder context = valid();
        context.alreadyVoted = true;
        assertThat(VotingPolicy.evaluate(context.build())).contains(VoteRejectionReason.ALREADY_VOTED);
        assertThat(VotingPolicy.evaluateAccess(context.build())).contains(VoteRejectionReason.ALREADY_VOTED);
    }

    @Test
    @DisplayName("FR-11: a vote without a selected candidate is refused")
    void missingSelectionIsRefused() {
        ContextBuilder context = valid();
        context.candidateSelected = false;
        context.candidateOnBallot = false;
        assertThat(VotingPolicy.evaluate(context.build())).contains(VoteRejectionReason.NO_CANDIDATE_SELECTED);
    }

    @Test
    @DisplayName("FR-11: a candidate that is not on this election's ballot is refused")
    void candidateOfAnotherElectionIsRefused() {
        ContextBuilder context = valid();
        context.candidateOnBallot = false;
        assertThat(VotingPolicy.evaluate(context.build())).contains(VoteRejectionReason.INVALID_CANDIDATE);
    }

    @Test
    @DisplayName("Opening the ballot checks account, eligibility, phase and duplicates, but not the candidate")
    void accessCheckIgnoresCandidateFacts() {
        ContextBuilder context = valid();
        context.candidateSelected = false;
        context.candidateOnBallot = false;
        assertThat(VotingPolicy.evaluateAccess(context.build())).isEmpty();
    }

    @Test
    @DisplayName("The first failing rule wins: account, then eligibility, then phase, then duplicate, then candidate")
    void ruleOrder() {
        ContextBuilder everythingWrong = valid();
        everythingWrong.voterMayVote = false;
        everythingWrong.eligible = false;
        everythingWrong.status = ElectionStatus.CLOSED;
        everythingWrong.alreadyVoted = true;
        everythingWrong.candidateSelected = false;
        assertThat(VotingPolicy.evaluate(everythingWrong.build())).contains(VoteRejectionReason.ACCOUNT_NOT_PERMITTED);

        everythingWrong.voterMayVote = true;
        assertThat(VotingPolicy.evaluate(everythingWrong.build())).contains(VoteRejectionReason.NOT_ELIGIBLE);

        everythingWrong.eligible = true;
        assertThat(VotingPolicy.evaluate(everythingWrong.build())).contains(VoteRejectionReason.ELECTION_CLOSED);

        everythingWrong.status = ElectionStatus.OPEN;
        assertThat(VotingPolicy.evaluate(everythingWrong.build())).contains(VoteRejectionReason.ALREADY_VOTED);

        everythingWrong.alreadyVoted = false;
        assertThat(VotingPolicy.evaluate(everythingWrong.build())).contains(VoteRejectionReason.NO_CANDIDATE_SELECTED);
    }
}
