package edu.comp5100.election.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.AuditService;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * NFR-11: a broken audit log must not weaken a voting rule. The AuditService is replaced by a
 * mock that always fails; everything else (voting service, repositories, database) is real.
 */
@IntegrationTest
@DisplayName("Voting rules when the audit log fails")
class AuditFailureIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @MockitoBean
    private AuditService auditService;
    @Autowired
    private VotingService votingService;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("NFR-11: duplicate and ineligible votes stay rejected, and nothing extra is stored, when auditing fails")
    void rulesHoldWhenAuditFails() {
        clock.setInstant(BASE);
        doThrow(new IllegalStateException("audit store unavailable"))
                .when(auditService).recordRejection(any(), any(), any(), any());
        Election election = data.openElection("Audit down", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        List<Candidate> candidates = data.candidatesOf(election);
        UserAccount voter = data.voter("voter");
        UserAccount outsider = data.voter("outsider");
        data.makeEligible(election, voter);

        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        assertThatThrownBy(() -> votingService.castVote(voter.getUsername(), election.getId(),
                candidates.get(1).getId())).isInstanceOfSatisfying(VoteRejectedException.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.ALREADY_VOTED));
        assertThatThrownBy(() -> votingService.castVote(outsider.getUsername(), election.getId(),
                candidates.get(1).getId())).isInstanceOfSatisfying(VoteRejectedException.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.NOT_ELIGIBLE));

        verify(auditService).recordRejection(voter.getUsername(), election.getId(),
                VoteRejectionReason.ALREADY_VOTED, AuditService.STAGE_VOTE_SUBMISSION);
        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(0).getId());
    }
}
