package edu.comp5100.election.service;

import edu.comp5100.election.domain.Election;
import edu.comp5100.election.policy.ElectionPhase;

/** One election on the voter home page: its phase and the voter's own participation status. */
public record VoterElectionRow(Election election, ElectionPhase phase, boolean eligible, boolean hasVoted,
        boolean canVote, boolean resultsVisible) {
}
