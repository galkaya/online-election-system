package edu.comp5100.election.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One accepted vote: election, candidate and the hash of the receipt code. It has no voter,
 * username, session, IP address, timestamp or link to a participation record. The key is a
 * random UUID so that it does not reveal the order in which ballots were stored.
 */
@Entity
@Table(name = "ballot")
public class Ballot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "receipt_hash", nullable = false, length = 64)
    private String receiptHash;

    protected Ballot() {
        // required by JPA
    }

    public Ballot(Long electionId, Long candidateId, String receiptHash) {
        this.electionId = electionId;
        this.candidateId = candidateId;
        this.receiptHash = receiptHash;
    }

    public UUID getId() {
        return id;
    }

    public Long getElectionId() {
        return electionId;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public String getReceiptHash() {
        return receiptHash;
    }
}
