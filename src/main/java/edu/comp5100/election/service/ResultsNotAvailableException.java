package edu.comp5100.election.service;

/** Results were requested while the election is still a draft, scheduled or open (FR-14). */
public class ResultsNotAvailableException extends RuntimeException {

    public ResultsNotAvailableException() {
        super("Results are published only after the election has ended or has been closed.");
    }
}
