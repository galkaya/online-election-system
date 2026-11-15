package edu.comp5100.election.domain;

import edu.comp5100.election.policy.ElectionLifecyclePolicy;
import edu.comp5100.election.policy.ElectionPhase;
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

/** An election with a voting window stored in UTC. */
@Entity
@Table(name = "election")
public class Election {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ElectionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Election() {
        // required by JPA
    }

    public Election(String title, String description, Instant startsAt, Instant endsAt, Instant createdAt) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = ElectionStatus.DRAFT;
        this.createdAt = createdAt;
    }

    /** Changes the editable setup. Callers must first check that the election is still a draft. */
    public void updateSetup(String title, String description, Instant startsAt, Instant endsAt) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public void markOpen() {
        this.status = ElectionStatus.OPEN;
    }

    public void markClosed() {
        this.status = ElectionStatus.CLOSED;
    }

    public ElectionPhase phaseAt(Instant now) {
        return ElectionLifecyclePolicy.phaseOf(status, startsAt, endsAt, now);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public ElectionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
