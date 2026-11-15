package edu.comp5100.election.policy;

import edu.comp5100.election.domain.ElectionStatus;
import java.time.Instant;

/**
 * Every fact the voting rules need, gathered by the service while it holds the election lock.
 *
 * @param voterMayVote the account exists, is enabled and has the VOTER role
 * @param eligible the voter has active eligibility for this election
 * @param alreadyVoted a participation record already exists for this voter and election
 * @param candidateSelected the request named a candidate
 * @param candidateOnBallot the named candidate belongs to this election
 */
public record VoteContext(
        boolean voterMayVote,
        boolean eligible,
        ElectionStatus status,
        Instant startsAt,
        Instant endsAt,
        Instant now,
        boolean alreadyVoted,
        boolean candidateSelected,
        boolean candidateOnBallot) {
}
