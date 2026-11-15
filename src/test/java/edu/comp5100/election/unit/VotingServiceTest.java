package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.service.AuditService;
import edu.comp5100.election.service.VoteNotProcessedException;
import edu.comp5100.election.service.VoteReceipt;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VoteTransaction;
import edu.comp5100.election.service.VotingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;

/** Unit tests for the orchestration around the voting transaction (FR-11, FR-15, NFR-11). */
@ExtendWith(MockitoExtension.class)
@DisplayName("Voting service (transaction and audit mocked)")
class VotingServiceTest {

    @Mock
    private VoteTransaction voteTransaction;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private VotingService votingService;

    @Test
    @DisplayName("FR-15: an accepted vote returns the receipt and writes no audit record tied to the voter")
    void acceptedVoteIsNotAudited() {
        when(voteTransaction.castVote("voter1", 5L, 31L)).thenReturn(new VoteReceipt("CODE"));

        assertThat(votingService.castVote("voter1", 5L, 31L).code()).isEqualTo("CODE");
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("FR-15: a rejected vote is audited with account, election and reason, then rethrown")
    void rejectedVoteIsAuditedAndRethrown() {
        when(voteTransaction.castVote("voter1", 5L, 31L))
                .thenThrow(new VoteRejectedException(VoteRejectionReason.ALREADY_VOTED));

        assertThatThrownBy(() -> votingService.castVote("voter1", 5L, 31L))
                .isInstanceOfSatisfying(VoteRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.ALREADY_VOTED));
        verify(auditService).recordRejection("voter1", 5L, VoteRejectionReason.ALREADY_VOTED,
                AuditService.STAGE_VOTE_SUBMISSION);
    }

    @Test
    @DisplayName("FR-15: a rejection for an unknown election is audited without an election id")
    void unknownElectionIsAuditedWithoutElectionId() {
        when(voteTransaction.castVote("voter1", 404L, 31L))
                .thenThrow(new VoteRejectedException(VoteRejectionReason.ELECTION_NOT_FOUND));

        assertThatThrownBy(() -> votingService.castVote("voter1", 404L, 31L))
                .isInstanceOf(VoteRejectedException.class);
        verify(auditService).recordRejection("voter1", null, VoteRejectionReason.ELECTION_NOT_FOUND,
                AuditService.STAGE_VOTE_SUBMISSION);
    }

    @Test
    @DisplayName("NFR-11: a failing audit log cannot turn a rejection into anything else")
    void auditFailureDoesNotMaskTheRejection() {
        when(voteTransaction.castVote("voter1", 5L, 31L))
                .thenThrow(new VoteRejectedException(VoteRejectionReason.NOT_ELIGIBLE));
        doThrow(new IllegalStateException("audit store unavailable"))
                .when(auditService).recordRejection(any(), any(), any(), any());

        assertThatThrownBy(() -> votingService.castVote("voter1", 5L, 31L))
                .isInstanceOfSatisfying(VoteRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.NOT_ELIGIBLE));
    }

    @Test
    @DisplayName("NFR-03: a lock timeout is reported as 'not processed', not as an accepted or rejected vote")
    void lockTimeoutIsReportedAsNotProcessed() {
        when(voteTransaction.castVote("voter1", 5L, 31L)).thenThrow(new CannotAcquireLockException("timeout"));

        assertThatThrownBy(() -> votingService.castVote("voter1", 5L, 31L))
                .isInstanceOf(VoteNotProcessedException.class);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("FR-15: a refused ballot request is audited as a ballot request")
    void refusedBallotRequestIsAudited() {
        when(voteTransaction.loadBallot("voter4", 5L))
                .thenThrow(new VoteRejectedException(VoteRejectionReason.NOT_ELIGIBLE));

        assertThatThrownBy(() -> votingService.openBallot("voter4", 5L)).isInstanceOf(VoteRejectedException.class);
        verify(auditService).recordRejection("voter4", 5L, VoteRejectionReason.NOT_ELIGIBLE,
                AuditService.STAGE_BALLOT_REQUEST);
    }
}
