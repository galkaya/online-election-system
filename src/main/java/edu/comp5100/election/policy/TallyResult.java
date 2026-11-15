package edu.comp5100.election.policy;

import java.util.List;

/**
 * Result of counting the stored ballots of one election.
 *
 * @param rows one row per candidate in ballot order, including candidates with zero votes
 * @param totalVotes number of accepted ballots
 * @param leaders the candidate(s) with the most votes; empty when there are no votes
 */
public record TallyResult(List<CandidateTally> rows, long totalVotes, TallyOutcome outcome,
        List<CandidateTally> leaders) {
}
