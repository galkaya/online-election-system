package edu.comp5100.election.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An administrative event or a rejected vote attempt. It may name the acting account, but it
 * never contains a candidate choice, a ballot id or a receipt code.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    @Column(length = 50)
    private String actor;

    @Column(name = "election_id")
    private Long electionId;

    @Column(nullable = false, length = 500)
    private String detail;

    protected AuditEvent() {
        // required by JPA
    }

    public AuditEvent(Instant occurredAt, AuditEventType eventType, String actor, Long electionId, String detail) {
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.actor = actor;
        this.electionId = electionId;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getActor() {
        return actor;
    }

    public Long getElectionId() {
        return electionId;
    }

    public String getDetail() {
        return detail;
    }
}
