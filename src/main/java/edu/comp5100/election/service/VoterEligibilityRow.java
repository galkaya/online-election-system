package edu.comp5100.election.service;

import edu.comp5100.election.domain.UserAccount;

/** A voter account as shown on the election administration page. */
public record VoterEligibilityRow(UserAccount voter, boolean eligible, boolean hasVoted) {
}
