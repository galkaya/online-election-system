package edu.comp5100.election.property;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.policy.ElectionLifecyclePolicy;
import edu.comp5100.election.policy.ElectionPhase;
import edu.comp5100.election.policy.VoteContext;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.policy.VotingPolicy;
import java.time.Instant;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;

/**
 * Property-based tests of the voting rules over valid and invalid combinations of every input
 * (FR-07, FR-10, FR-11), run by the jqwik engine. The oracle is the written requirement, "a vote
 * is accepted exactly when every condition holds", not a copy of the rule order in the code.
 */
@Label("Voting rule properties (jqwik)")
class VotingRuleProperties {

    private static final Instant START = Instant.parse("2026-11-19T15:00:00Z");
    private static final long WINDOW_SECONDS = 3600;
    private static final Instant END = START.plusSeconds(WINDOW_SECONDS);

    @Property(tries = 2000)
    @Label("A vote is accepted exactly when every requirement holds; otherwise it has a rejection reason")
    void acceptedExactlyWhenEveryRuleHolds(@ForAll boolean voterMayVote, @ForAll boolean eligible,
            @ForAll ElectionStatus status, @ForAll @LongRange(min = -7200, max = 7200) long secondsAfterStart,
            @ForAll boolean alreadyVoted, @ForAll boolean candidateSelected, @ForAll boolean candidateOnBallot) {
        Instant now = START.plusSeconds(secondsAfterStart);
        VoteContext context = new VoteContext(voterMayVote, eligible, status, START, END, now, alreadyVoted,
                candidateSelected, candidateOnBallot);

        boolean insideWindow = secondsAfterStart >= 0 && secondsAfterStart < WINDOW_SECONDS;
        boolean requirementsHold = voterMayVote && eligible && status == ElectionStatus.OPEN && insideWindow
                && !alreadyVoted && candidateSelected && candidateOnBallot;

        Optional<VoteRejectionReason> decision = VotingPolicy.evaluate(context);

        assertThat(decision.isEmpty()).isEqualTo(requirementsHold);
    }

    @Property(tries = 2000)
    @Label("Whoever may not open the ballot can never cast a vote, and for the same reason")
    void accessRejectionCarriesOverToVoting(@ForAll boolean voterMayVote, @ForAll boolean eligible,
            @ForAll ElectionStatus status, @ForAll @LongRange(min = -7200, max = 7200) long secondsAfterStart,
            @ForAll boolean alreadyVoted, @ForAll boolean candidateSelected, @ForAll boolean candidateOnBallot) {
        VoteContext context = new VoteContext(voterMayVote, eligible, status, START, END,
                START.plusSeconds(secondsAfterStart), alreadyVoted, candidateSelected, candidateOnBallot);

        Optional<VoteRejectionReason> access = VotingPolicy.evaluateAccess(context);

        if (access.isPresent()) {
            assertThat(VotingPolicy.evaluate(context)).isEqualTo(access);
        }
    }

    @Property(tries = 2000)
    @Label("For any window, an activated election is in exactly one of: not started, voting, ended")
    void windowAndPhaseAgree(@ForAll @LongRange(min = 1, max = 1_000_000) long windowSeconds,
            @ForAll @LongRange(min = -2_000_000, max = 2_000_000) long secondsAfterStart) {
        Instant end = START.plusSeconds(windowSeconds);
        Instant now = START.plusSeconds(secondsAfterStart);

        boolean inside = ElectionLifecyclePolicy.isWithinVotingWindow(START, end, now);
        ElectionPhase phase = ElectionLifecyclePolicy.phaseOf(ElectionStatus.OPEN, START, end, now);

        assertThat(inside).isEqualTo(secondsAfterStart >= 0 && secondsAfterStart < windowSeconds);
        assertThat(phase == ElectionPhase.VOTING).isEqualTo(inside);
        if (secondsAfterStart < 0) {
            assertThat(phase).isEqualTo(ElectionPhase.SCHEDULED);
        }
        if (secondsAfterStart >= windowSeconds) {
            assertThat(phase).isEqualTo(ElectionPhase.ENDED);
            assertThat(ElectionLifecyclePolicy.resultsVisible(phase)).isTrue();
        }
    }
}
