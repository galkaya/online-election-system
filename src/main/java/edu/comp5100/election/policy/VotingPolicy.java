package edu.comp5100.election.policy;

import java.util.Optional;

/**
 * Pure decision rules for accepting a vote (FR-07, FR-09, FR-10, FR-11, FR-16, FR-17).
 * The order of the checks is fixed and documented in docs/ARCHITECTURE.md.
 */
public final class VotingPolicy {

    private VotingPolicy() {
    }

    /**
     * Checks whether the voter may open the ballot of this election at all: account, eligibility,
     * election phase and the one-vote rule. Returns the first rule that fails, or empty.
     */
    public static Optional<VoteRejectionReason> evaluateAccess(VoteContext context) {
        if (!context.voterMayVote()) {
            return Optional.of(VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
        }
        if (!context.eligible()) {
            return Optional.of(VoteRejectionReason.NOT_ELIGIBLE);
        }
        ElectionPhase phase = ElectionLifecyclePolicy.phaseOf(
                context.status(), context.startsAt(), context.endsAt(), context.now());
        Optional<VoteRejectionReason> phaseProblem = switch (phase) {
            case DRAFT -> Optional.of(VoteRejectionReason.ELECTION_NOT_OPEN);
            case SCHEDULED -> Optional.of(VoteRejectionReason.VOTING_NOT_STARTED);
            case ENDED -> Optional.of(VoteRejectionReason.VOTING_ENDED);
            case CLOSED -> Optional.of(VoteRejectionReason.ELECTION_CLOSED);
            case VOTING -> Optional.empty();
        };
        if (phaseProblem.isPresent()) {
            return phaseProblem;
        }
        if (context.alreadyVoted()) {
            return Optional.of(VoteRejectionReason.ALREADY_VOTED);
        }
        return Optional.empty();
    }

    /** Full decision for a submitted vote: the access rules, then the candidate rules. */
    public static Optional<VoteRejectionReason> evaluate(VoteContext context) {
        Optional<VoteRejectionReason> accessProblem = evaluateAccess(context);
        if (accessProblem.isPresent()) {
            return accessProblem;
        }
        if (!context.candidateSelected()) {
            return Optional.of(VoteRejectionReason.NO_CANDIDATE_SELECTED);
        }
        if (!context.candidateOnBallot()) {
            return Optional.of(VoteRejectionReason.INVALID_CANDIDATE);
        }
        return Optional.empty();
    }
}
