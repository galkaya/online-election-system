package edu.comp5100.election.service;

import edu.comp5100.election.domain.Candidate;
import java.util.List;

/** Everything the election administration page needs. */
public record ElectionAdminDetail(ElectionOverview overview, List<Candidate> candidates,
        List<VoterEligibilityRow> voters, boolean setupEditable, boolean closable, boolean eligibilityManageable) {
}
