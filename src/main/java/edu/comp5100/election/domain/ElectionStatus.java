package edu.comp5100.election.domain;

/**
 * Administrative life cycle of an election: DRAFT, then OPEN, then CLOSED. There is no way back.
 * OPEN means "activated"; whether votes are accepted also depends on the time window.
 */
public enum ElectionStatus {
    DRAFT,
    OPEN,
    CLOSED
}
