package edu.comp5100.election.policy;

/** How a tally ends. A tie is reported as a tie; the system never picks an arbitrary winner. */
public enum TallyOutcome {
    NO_VOTES,
    WINNER,
    TIE
}
