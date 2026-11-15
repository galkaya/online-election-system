package edu.comp5100.election.repository;

import edu.comp5100.election.domain.AuditEvent;
import edu.comp5100.election.domain.AuditEventType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop200ByOrderByOccurredAtDescIdDesc();

    long countByElectionIdAndEventType(Long electionId, AuditEventType eventType);

    long countByEventType(AuditEventType eventType);
}
