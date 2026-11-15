package edu.comp5100.election.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * States that a voter may vote in an election. Revoking sets {@code active} to false instead of
 * deleting the row. This record never holds a candidate, a ballot id or a receipt.
 */
@Entity
@Table(name = "election_eligibility")
public class ElectionEligibility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "voter_id", nullable = false)
    private Long voterId;

    @Column(nullable = false)
    private boolean active;

    protected ElectionEligibility() {
        // required by JPA
    }

    public ElectionEligibility(Long electionId, Long voterId) {
        this.electionId = electionId;
        this.voterId = voterId;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Long getElectionId() {
        return electionId;
    }

    public Long getVoterId() {
        return voterId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
