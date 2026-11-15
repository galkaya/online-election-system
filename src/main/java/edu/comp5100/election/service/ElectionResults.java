package edu.comp5100.election.service;

import edu.comp5100.election.domain.Election;
import edu.comp5100.election.policy.ElectionPhase;
import edu.comp5100.election.policy.TallyResult;

/** The published result of a finished election. */
public record ElectionResults(Election election, ElectionPhase phase, TallyResult tally, long eligibleCount) {
}
