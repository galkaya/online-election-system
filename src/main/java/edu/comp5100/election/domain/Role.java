package edu.comp5100.election.domain;

/** Account roles. An account has exactly one role; only VOTER accounts can cast ballots. */
public enum Role {
    ADMIN,
    VOTER
}
