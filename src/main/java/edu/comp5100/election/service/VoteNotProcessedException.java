package edu.comp5100.election.service;

/**
 * The vote could not be processed for a technical reason (for example a lock timeout).
 * Nothing was stored; the voter should check the participation status before trying again.
 */
public class VoteNotProcessedException extends RuntimeException {

    public VoteNotProcessedException(Throwable cause) {
        super("The vote could not be processed.", cause);
    }
}
