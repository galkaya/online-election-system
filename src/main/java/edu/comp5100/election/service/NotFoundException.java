package edu.comp5100.election.service;

/** The requested election, candidate or account does not exist or is not visible to the caller. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
