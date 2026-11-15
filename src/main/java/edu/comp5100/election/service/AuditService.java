package edu.comp5100.election.service;

import edu.comp5100.election.domain.AuditEvent;
import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.repository.AuditEventRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes and reads audit records (FR-15, NFR-11). Audit records never contain a candidate choice,
 * a ballot id or a receipt code, and no record is written for an accepted vote.
 */
@Service
public class AuditService {

    /** Marks a rejection that happened when the voter asked for the ballot page. */
    public static final String STAGE_BALLOT_REQUEST = "ballot request";
    /** Marks a rejection that happened when the voter submitted a vote. */
    public static final String STAGE_VOTE_SUBMISSION = "vote submission";

    private final AuditEventRepository auditEventRepository;
    private final Clock clock;

    public AuditService(AuditEventRepository auditEventRepository, Clock clock) {
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
    }

    /** Records an administrative event inside the caller's transaction: both commit or neither. */
    @Transactional
    public void recordAdminEvent(AuditEventType type, String actor, Long electionId, String detail) {
        auditEventRepository.save(new AuditEvent(clock.instant(), type, actor, electionId, limit(detail)));
    }

    /**
     * Records a rejected vote attempt in its own transaction. The voting service calls this after
     * the rejected voting transaction has rolled back, so the record is not lost with it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejection(String actor, Long electionId, VoteRejectionReason reason, String stage) {
        String detail = "Reason: " + reason.name() + " (" + stage + ")";
        auditEventRepository.save(new AuditEvent(clock.instant(), AuditEventType.VOTE_REJECTED, actor, electionId,
                detail));
    }

    @Transactional(readOnly = true)
    public List<AuditEvent> recentEvents() {
        return auditEventRepository.findTop200ByOrderByOccurredAtDescIdDesc();
    }

    private static String limit(String detail) {
        return detail.length() <= 500 ? detail : detail.substring(0, 497) + "...";
    }
}
