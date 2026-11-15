package edu.comp5100.election.policy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts ballots (FR-14). The input is the candidate id of every stored ballot; there are no
 * separate counters that could drift away from the ballots.
 */
public final class TallyCalculator {

    private TallyCalculator() {
    }

    public static TallyResult tally(List<CandidateOption> candidates, Collection<Long> ballotCandidateIds) {
        Map<Long, Long> votesByCandidate = new LinkedHashMap<>();
        for (CandidateOption candidate : candidates) {
            if (votesByCandidate.put(candidate.id(), 0L) != null) {
                throw new IllegalArgumentException("Candidate listed twice: " + candidate.id());
            }
        }
        for (Long candidateId : ballotCandidateIds) {
            if (candidateId == null || !votesByCandidate.containsKey(candidateId)) {
                throw new IllegalStateException("A stored ballot names a candidate that is not on this ballot.");
            }
            votesByCandidate.merge(candidateId, 1L, Long::sum);
        }

        long totalVotes = ballotCandidateIds.size();
        List<CandidateTally> rows = candidates.stream()
                .map(candidate -> {
                    long votes = votesByCandidate.get(candidate.id());
                    return new CandidateTally(candidate.id(), candidate.name(), votes, percent(votes, totalVotes));
                })
                .toList();

        if (totalVotes == 0) {
            return new TallyResult(rows, 0, TallyOutcome.NO_VOTES, List.of());
        }
        long highest = rows.stream().mapToLong(CandidateTally::votes).max().orElse(0);
        List<CandidateTally> leaders = rows.stream().filter(row -> row.votes() == highest).toList();
        TallyOutcome outcome = leaders.size() == 1 ? TallyOutcome.WINNER : TallyOutcome.TIE;
        return new TallyResult(rows, totalVotes, outcome, leaders);
    }

    /** Share of the total as a percentage with one decimal place. */
    public static double percent(long votes, long totalVotes) {
        if (totalVotes == 0) {
            return 0.0;
        }
        return Math.round(votes * 1000.0 / totalVotes) / 10.0;
    }
}
