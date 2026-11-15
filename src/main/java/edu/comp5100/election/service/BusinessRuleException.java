package edu.comp5100.election.service;

/** An administrative action broke a business rule. The message is safe to show to the user. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
