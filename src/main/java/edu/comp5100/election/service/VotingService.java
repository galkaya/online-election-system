package edu.comp5100.election.service;

import edu.comp5100.election.policy.VoteRejectionReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Entry point for voting (FR-09 to FR-12). This class is deliberately not transactional: it runs
 * the voting transaction, and only after that transaction has finished (committed or rolled back)
 * does it write the audit record of a rejection in a separate transaction (NFR-11).
 */
@Service
public class VotingService {

    private static final Logger log = LoggerFactory.getLogger(VotingService.class);

    private final VoteTransaction voteTransaction;
    private final AuditService auditService;

    public VotingService(VoteTransaction voteTransaction, AuditService auditService) {
        this.voteTransaction = voteTransaction;
        this.auditService = auditService;
    }

    /**
     * Casts one vote for the authenticated voter.
     *
     * @throws VoteRejectedException when a voting rule refuses the vote; nothing is stored
     * @throws VoteNotProcessedException when the database could not process the vote; nothing is stored
     */
    public VoteReceipt castVote(String username, long electionId, Long candidateId) {
        try {
            return voteTransaction.castVote(username, electionId, candidateId);
        } catch (VoteRejectedException rejected) {
            auditRejection(username, electionId, rejected.reason(), AuditService.STAGE_VOTE_SUBMISSION);
            throw rejected;
        } catch (DataAccessException technicalFailure) {
            // Lock timeout or an unexpected constraint failure. The transaction has rolled back.
            log.warn("A vote could not be processed and was rolled back: {}",
                    technicalFailure.getClass().getSimpleName());
            throw new VoteNotProcessedException(technicalFailure);
        }
    }

    /** Opens the ballot for the authenticated voter, or throws the reason why voting is not possible. */
    public BallotView openBallot(String username, long electionId) {
        try {
            return voteTransaction.loadBallot(username, electionId);
        } catch (VoteRejectedException rejected) {
            auditRejection(username, electionId, rejected.reason(), AuditService.STAGE_BALLOT_REQUEST);
            throw rejected;
        }
    }

    /**
     * An audit failure is logged and swallowed on purpose: the vote has already been refused and
     * rolled back, and a broken audit log must never turn that refusal into anything else.
     */
    private void auditRejection(String username, long electionId, VoteRejectionReason reason, String stage) {
        try {
            Long auditedElectionId = reason == VoteRejectionReason.ELECTION_NOT_FOUND ? null : electionId;
            auditService.recordRejection(username, auditedElectionId, reason, stage);
        } catch (RuntimeException auditFailure) {
            log.error("The audit record of a rejected vote attempt could not be stored (reason {}).", reason,
                    auditFailure);
        }
    }
}
