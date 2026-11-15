package edu.comp5100.election.service;

import edu.comp5100.election.policy.VoteRejectionReason;

/** A vote attempt was refused by a voting rule. Rolls back the voting transaction. */
public class VoteRejectedException extends RuntimeException {

    private final VoteRejectionReason reason;

    public VoteRejectedException(VoteRejectionReason reason) {
        super(reason.message());
        this.reason = reason;
    }

    public VoteRejectionReason reason() {
        return reason;
    }
}
