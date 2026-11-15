package edu.comp5100.election.service;

/**
 * The confirmation code of an accepted vote, in display form. It exists only in the response to
 * the voter; the database keeps a hash.
 */
public record VoteReceipt(String code) {
}
