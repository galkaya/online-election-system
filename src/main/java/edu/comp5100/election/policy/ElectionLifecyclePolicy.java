package edu.comp5100.election.policy;

import edu.comp5100.election.domain.ElectionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Pure rules for the election life cycle and the voting window. No Spring, no database.
 * The current time is always passed in, so the rules are testable with a fixed clock.
 */
public final class ElectionLifecyclePolicy {

    public static final int MINIMUM_CANDIDATES = 2;

    private ElectionLifecyclePolicy() {
    }

    /** A window is valid when both ends exist and the start is strictly before the end. */
    public static boolean isValidWindow(Instant startsAt, Instant endsAt) {
        return startsAt != null && endsAt != null && startsAt.isBefore(endsAt);
    }

    /**
     * FR-07: {@code startsAt <= now < endsAt}. The start instant is accepted, the end instant is not.
     */
    public static boolean isWithinVotingWindow(Instant startsAt, Instant endsAt, Instant now) {
        return !now.isBefore(startsAt) && now.isBefore(endsAt);
    }

    public static ElectionPhase phaseOf(ElectionStatus status, Instant startsAt, Instant endsAt, Instant now) {
        return switch (status) {
            case DRAFT -> ElectionPhase.DRAFT;
            case CLOSED -> ElectionPhase.CLOSED;
            case OPEN -> {
                if (now.isBefore(startsAt)) {
                    yield ElectionPhase.SCHEDULED;
                }
                if (now.isBefore(endsAt)) {
                    yield ElectionPhase.VOTING;
                }
                yield ElectionPhase.ENDED;
            }
        };
    }

    /** Returns every reason that blocks activation; an empty list means the draft can be activated. */
    public static List<ActivationProblem> activationProblems(ElectionStatus status, Instant startsAt, Instant endsAt,
            Collection<String> candidateNames, Instant now) {
        List<ActivationProblem> problems = new ArrayList<>();
        if (status != ElectionStatus.DRAFT) {
            problems.add(ActivationProblem.NOT_A_DRAFT);
            return problems;
        }
        if (!isValidWindow(startsAt, endsAt)) {
            problems.add(ActivationProblem.INVALID_WINDOW);
        } else if (!now.isBefore(endsAt)) {
            problems.add(ActivationProblem.DEADLINE_PASSED);
        }
        if (countDistinctValidNames(candidateNames) < MINIMUM_CANDIDATES) {
            problems.add(ActivationProblem.TOO_FEW_CANDIDATES);
        }
        return problems;
    }

    /** Counts candidate names that are not blank, ignoring case and surrounding spaces for duplicates. */
    public static long countDistinctValidNames(Collection<String> candidateNames) {
        return candidateNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
    }

    /** Title, description, window and candidates can change only while the election is a draft. */
    public static boolean canEditSetup(ElectionStatus status) {
        return status == ElectionStatus.DRAFT;
    }

    /** Only an activated election can be closed; a closed election is never reopened. */
    public static boolean canClose(ElectionStatus status) {
        return status == ElectionStatus.OPEN;
    }

    /** FR-14: results exist for viewers only after closing or after the deadline. */
    public static boolean resultsVisible(ElectionPhase phase) {
        return phase == ElectionPhase.ENDED || phase == ElectionPhase.CLOSED;
    }

    /** Eligibility can be granted or revoked until voting is over. */
    public static boolean canManageEligibility(ElectionPhase phase) {
        return phase == ElectionPhase.DRAFT || phase == ElectionPhase.SCHEDULED || phase == ElectionPhase.VOTING;
    }
}
