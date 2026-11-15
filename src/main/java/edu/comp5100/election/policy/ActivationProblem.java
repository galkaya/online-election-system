package edu.comp5100.election.policy;

/** Reasons why a draft election cannot be activated (FR-06). */
public enum ActivationProblem {
    NOT_A_DRAFT("Only a draft election can be activated."),
    INVALID_WINDOW("The start time must be before the end time."),
    DEADLINE_PASSED("The end time has already passed. Choose a later end time before activating."),
    TOO_FEW_CANDIDATES("At least two candidates with different names are required.");

    private final String message;

    ActivationProblem(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
