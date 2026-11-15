package edu.comp5100.election.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.ReceiptGenerator;
import edu.comp5100.election.service.ReceiptStatus;
import edu.comp5100.election.service.ReceiptVerificationService;
import edu.comp5100.election.service.VoteNotProcessedException;
import edu.comp5100.election.service.VoteReceipt;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Forced failure of the second critical write (NFR-02). The only test hook is a Mockito spy on
 * ReceiptGenerator that exists in this test context alone: it makes the generator return a code
 * that is already stored, so the real database rejects the ballot insert with its unique
 * constraint after the participation row was already written in the same transaction.
 */
@IntegrationTest
@DisplayName("Rollback when the ballot write fails")
class VoteRollbackIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @MockitoSpyBean
    private ReceiptGenerator receiptGenerator;
    @Autowired
    private VotingService votingService;
    @Autowired
    private ReceiptVerificationService receiptVerificationService;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    @Test
    @DisplayName("NFR-02: when the ballot insert fails, the participation is rolled back and the voter can vote again")
    void failedBallotWriteLeavesNoHalfVote() {
        clock.setInstant(BASE);
        Election election = data.openElection("Rollback", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        List<Candidate> candidates = data.candidatesOf(election);
        UserAccount first = data.voter("first");
        UserAccount second = data.voter("second");
        data.makeEligible(election, first, second);

        VoteReceipt firstReceipt = votingService.castVote(first.getUsername(), election.getId(),
                candidates.get(0).getId());
        String storedCode = ReceiptCodec.normalize(firstReceipt.code()).orElseThrow();

        // Force the next ballot insert to violate the unique receipt-hash constraint.
        doReturn(storedCode).when(receiptGenerator).newCode();
        assertThatThrownBy(() -> votingService.castVote(second.getUsername(), election.getId(),
                candidates.get(1).getId())).isInstanceOf(VoteNotProcessedException.class);

        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(participationRepository.existsByElectionIdAndVoterId(election.getId(), second.getId())).isFalse();
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(0).getId());

        // With the fault removed, the same voter is not locked out: the vote goes through once.
        doCallRealMethod().when(receiptGenerator).newCode();
        VoteReceipt secondReceipt = votingService.castVote(second.getUsername(), election.getId(),
                candidates.get(1).getId());

        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(2);
        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(2);
        assertThat(receiptVerificationService.verify(secondReceipt.code())).isEqualTo(ReceiptStatus.ACCEPTED);
        assertThat(receiptVerificationService.verify(firstReceipt.code())).isEqualTo(ReceiptStatus.ACCEPTED);
    }
}
