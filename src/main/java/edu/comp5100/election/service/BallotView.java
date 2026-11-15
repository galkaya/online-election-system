package edu.comp5100.election.service;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import java.util.List;

/** What the ballot page shows: the election and its candidates in ballot order. */
public record BallotView(Election election, List<Candidate> candidates) {

    public Candidate candidate(long candidateId) {
        return candidates.stream().filter(candidate -> candidate.getId() == candidateId).findFirst().orElse(null);
    }
}
