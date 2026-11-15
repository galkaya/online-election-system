package edu.comp5100.election.policy;

/** Why a vote attempt was refused, with the English message shown to the voter (FR-11). */
public enum VoteRejectionReason {
    ELECTION_NOT_FOUND("This election does not exist."),
    ACCOUNT_NOT_PERMITTED("This account is not allowed to vote. Only enabled voter accounts can cast ballots."),
    NOT_ELIGIBLE("You are not on the eligibility list for this election."),
    ELECTION_NOT_OPEN("This election has not been opened for voting."),
    VOTING_NOT_STARTED("Voting for this election has not started yet."),
    VOTING_ENDED("The voting period for this election has ended."),
    ELECTION_CLOSED("This election has been closed by the administrator."),
    ALREADY_VOTED("You have already voted in this election. Each voter can vote only once."),
    NO_CANDIDATE_SELECTED("Select one candidate before continuing."),
    INVALID_CANDIDATE("The selected candidate is not on the ballot for this election.");

    private final String message;

    VoteRejectionReason(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
