package edu.comp5100.election.service;

/** The only three answers of receipt verification. None of them reveals a voter or a candidate. */
public enum ReceiptStatus {
    /** A stored ballot carries this code: the vote is in the tally. */
    ACCEPTED,
    /** The code is well formed but no stored ballot carries it. */
    NOT_FOUND,
    /** The text cannot be a confirmation code. */
    MALFORMED
}
