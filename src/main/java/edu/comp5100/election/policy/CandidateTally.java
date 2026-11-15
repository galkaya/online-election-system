package edu.comp5100.election.policy;

/**
 * One row of the result table.
 *
 * @param percent share of all accepted votes, rounded to one decimal place; 0.0 when there are no votes
 */
public record CandidateTally(long candidateId, String name, long votes, double percent) {
}
