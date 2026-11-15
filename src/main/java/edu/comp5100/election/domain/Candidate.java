package edu.comp5100.election.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A fictional candidate that belongs to exactly one election. */
@Entity
@Table(name = "candidate")
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "election_id", nullable = false)
    private Long electionId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String biography;

    protected Candidate() {
        // required by JPA
    }

    public Candidate(Long electionId, String name, String biography) {
        this.electionId = electionId;
        this.name = name;
        this.biography = biography;
    }

    public Long getId() {
        return id;
    }

    public Long getElectionId() {
        return electionId;
    }

    public String getName() {
        return name;
    }

    public String getBiography() {
        return biography;
    }
}
