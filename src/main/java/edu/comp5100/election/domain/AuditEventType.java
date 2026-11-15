package edu.comp5100.election.domain;

/** Kinds of audit records. There is intentionally no "vote accepted" event tied to a user. */
public enum AuditEventType {
    ELECTION_CREATED("Election created"),
    ELECTION_UPDATED("Election updated"),
    CANDIDATE_ADDED("Candidate added"),
    CANDIDATE_REMOVED("Candidate removed"),
    ELECTION_ACTIVATED("Election activated"),
    ELECTION_CLOSED("Election closed"),
    ELIGIBILITY_GRANTED("Eligibility granted"),
    ELIGIBILITY_REVOKED("Eligibility revoked"),
    VOTER_CREATED("Voter account created"),
    VOTER_ENABLED("Voter account enabled"),
    VOTER_DISABLED("Voter account disabled"),
    VOTE_REJECTED("Vote attempt rejected");

    private final String label;

    AuditEventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
