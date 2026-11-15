package edu.comp5100.election.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * States that a voter has voted in an election, and nothing else: no candidate, no ballot id,
 * no receipt and no timestamp. The unique constraint on (election_id, voter_id) is the one-vote rule.
 */
@Entity
@Table(name = "participation")
public class Participation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "voter_id", nullable = false)
    private Long voterId;

    protected Participation() {
        // required by JPA
    }

    public Participation(Long electionId, Long voterId) {
        this.electionId = electionId;
        this.voterId = voterId;
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
}
