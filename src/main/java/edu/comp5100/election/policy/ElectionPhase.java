package edu.comp5100.election.policy;

/**
 * What an election looks like at a given moment. It combines the administrative status with the
 * voting window, so it can change without anybody editing the election (FR-06, FR-07).
 */
public enum ElectionPhase {
    /** Being prepared; not visible to voters. */
    DRAFT("Draft"),
    /** Activated, but the start time has not been reached. */
    SCHEDULED("Not started"),
    /** Activated and inside the window: votes are accepted. */
    VOTING("Voting open"),
    /** Activated, deadline passed, not yet closed by an administrator. Votes are rejected. */
    ENDED("Voting ended"),
    /** Closed by an administrator. Final. */
    CLOSED("Closed");

    private final String label;

    ElectionPhase(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
