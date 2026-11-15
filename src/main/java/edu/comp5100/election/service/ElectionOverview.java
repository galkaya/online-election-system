package edu.comp5100.election.service;

import edu.comp5100.election.domain.Election;
import edu.comp5100.election.policy.ElectionPhase;

/**
 * One election with the counts an administrator may always see. Turnout is a count of
 * participation records; it says nothing about which candidate anyone chose.
 */
public record ElectionOverview(Election election, ElectionPhase phase, long candidateCount, long eligibleCount,
        long participationCount, long ballotCount, long rejectedAttempts, boolean resultsVisible) {

    /** Integrity check shown on the audit page: every participation has exactly one ballot. */
    public boolean countsMatch() {
        return participationCount == ballotCount;
    }
}
