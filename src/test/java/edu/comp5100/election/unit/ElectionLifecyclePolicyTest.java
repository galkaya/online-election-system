package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.policy.ActivationProblem;
import edu.comp5100.election.policy.ElectionLifecyclePolicy;
import edu.comp5100.election.policy.ElectionPhase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Unit tests (no Spring, no database) for FR-06, FR-07 and FR-14: life cycle and voting window rules. */
@DisplayName("Election life cycle policy")
class ElectionLifecyclePolicyTest {

    private static final Instant START = Instant.parse("2026-11-19T15:00:00Z");
    private static final Instant END = Instant.parse("2026-11-20T15:00:00Z");
    private static final List<String> TWO_CANDIDATES = List.of("Jordan Lee", "Priya Nair");

    @ParameterizedTest(name = "{0} -> votes accepted: {2}")
    @CsvSource({
            "one second before the start, 2026-11-19T14:59:59Z, false",
            "exactly at the start,        2026-11-19T15:00:00Z, true",
            "one second after the start,  2026-11-19T15:00:01Z, true",
            "in the middle,               2026-11-20T03:00:00Z, true",
            "one second before the end,   2026-11-20T14:59:59Z, true",
            "exactly at the end,          2026-11-20T15:00:00Z, false",
            "one second after the end,    2026-11-20T15:00:01Z, false"
    })
    @DisplayName("FR-07: the voting window is startsAt <= now < endsAt")
    void votingWindowBoundaries(String description, Instant now, boolean expected) {
        assertThat(ElectionLifecyclePolicy.isWithinVotingWindow(START, END, now)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "status {0} at {1} -> phase {2}")
    @CsvSource({
            "DRAFT,  2026-11-19T14:00:00Z, DRAFT",
            "DRAFT,  2026-11-20T03:00:00Z, DRAFT",
            "DRAFT,  2026-11-21T00:00:00Z, DRAFT",
            "OPEN,   2026-11-19T14:59:59Z, SCHEDULED",
            "OPEN,   2026-11-19T15:00:00Z, VOTING",
            "OPEN,   2026-11-20T14:59:59Z, VOTING",
            "OPEN,   2026-11-20T15:00:00Z, ENDED",
            "OPEN,   2026-11-25T00:00:00Z, ENDED",
            "CLOSED, 2026-11-19T14:00:00Z, CLOSED",
            "CLOSED, 2026-11-20T03:00:00Z, CLOSED",
            "CLOSED, 2026-11-25T00:00:00Z, CLOSED"
    })
    @DisplayName("FR-06: the phase combines the administrative status with the clock")
    void phaseTable(ElectionStatus status, Instant now, ElectionPhase expected) {
        assertThat(ElectionLifecyclePolicy.phaseOf(status, START, END, now)).isEqualTo(expected);
    }

    @Test
    @DisplayName("FR-06: a draft with two candidates and a future deadline can be activated")
    void validDraftHasNoActivationProblems() {
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END, TWO_CANDIDATES,
                START.minusSeconds(3600))).isEmpty();
    }

    @Test
    @DisplayName("FR-06: a draft may be activated while its window is already running")
    void draftCanBeActivatedInsideItsWindow() {
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END, TWO_CANDIDATES,
                END.minusSeconds(1))).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ElectionStatus.class, names = {"OPEN", "CLOSED"})
    @DisplayName("FR-06: only a draft can be activated, so a closed election is never reopened")
    void onlyDraftsCanBeActivated(ElectionStatus status) {
        assertThat(ElectionLifecyclePolicy.activationProblems(status, START, END, TWO_CANDIDATES, START))
                .containsExactly(ActivationProblem.NOT_A_DRAFT);
    }

    @Test
    @DisplayName("FR-06: activation is refused when the deadline has been reached or passed")
    void activationRefusedAfterDeadline() {
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END, TWO_CANDIDATES, END))
                .containsExactly(ActivationProblem.DEADLINE_PASSED);
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END, TWO_CANDIDATES,
                END.plusSeconds(1))).containsExactly(ActivationProblem.DEADLINE_PASSED);
    }

    @Test
    @DisplayName("FR-06: activation is refused when the start is not before the end")
    void activationRefusedForInvalidWindow() {
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, END, START, TWO_CANDIDATES,
                START.minusSeconds(10))).containsExactly(ActivationProblem.INVALID_WINDOW);
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, START, TWO_CANDIDATES,
                START.minusSeconds(10))).containsExactly(ActivationProblem.INVALID_WINDOW);
    }

    @Test
    @DisplayName("FR-06: activation needs at least two candidates with different names")
    void activationNeedsTwoDistinctCandidates() {
        Instant now = START.minusSeconds(10);
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END, List.of(), now))
                .containsExactly(ActivationProblem.TOO_FEW_CANDIDATES);
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END,
                List.of("Jordan Lee"), now)).containsExactly(ActivationProblem.TOO_FEW_CANDIDATES);
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END,
                List.of("Jordan Lee", " jordan lee "), now)).containsExactly(ActivationProblem.TOO_FEW_CANDIDATES);
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END,
                List.of("Jordan Lee", "   "), now)).containsExactly(ActivationProblem.TOO_FEW_CANDIDATES);
    }

    @Test
    @DisplayName("FR-06: every activation problem is reported, not only the first")
    void allActivationProblemsAreReported() {
        assertThat(ElectionLifecyclePolicy.activationProblems(ElectionStatus.DRAFT, START, END,
                List.of("Jordan Lee"), END.plusSeconds(60)))
                .containsExactly(ActivationProblem.DEADLINE_PASSED, ActivationProblem.TOO_FEW_CANDIDATES);
    }

    @Test
    @DisplayName("FR-03: a window is valid only when the start is strictly before the end")
    void windowValidity() {
        assertThat(ElectionLifecyclePolicy.isValidWindow(START, END)).isTrue();
        assertThat(ElectionLifecyclePolicy.isValidWindow(START, START)).isFalse();
        assertThat(ElectionLifecyclePolicy.isValidWindow(END, START)).isFalse();
        assertThat(ElectionLifecyclePolicy.isValidWindow(null, END)).isFalse();
        assertThat(ElectionLifecyclePolicy.isValidWindow(START, null)).isFalse();
    }

    @ParameterizedTest(name = "status {0} -> setup editable: {1}, closable: {2}")
    @CsvSource({"DRAFT, true, false", "OPEN, false, true", "CLOSED, false, false"})
    @DisplayName("FR-06: setup is editable only in DRAFT, and only OPEN can be closed")
    void editAndCloseRules(ElectionStatus status, boolean editable, boolean closable) {
        assertThat(ElectionLifecyclePolicy.canEditSetup(status)).isEqualTo(editable);
        assertThat(ElectionLifecyclePolicy.canClose(status)).isEqualTo(closable);
    }

    @ParameterizedTest(name = "phase {0} -> results visible: {1}, eligibility manageable: {2}")
    @CsvSource({
            "DRAFT, false, true",
            "SCHEDULED, false, true",
            "VOTING, false, true",
            "ENDED, true, false",
            "CLOSED, true, false"
    })
    @DisplayName("FR-14: results are visible only after the end; eligibility is manageable only before it")
    void phaseDependentRules(ElectionPhase phase, boolean resultsVisible, boolean eligibilityManageable) {
        assertThat(ElectionLifecyclePolicy.resultsVisible(phase)).isEqualTo(resultsVisible);
        assertThat(ElectionLifecyclePolicy.canManageEligibility(phase)).isEqualTo(eligibilityManageable);
    }
}
