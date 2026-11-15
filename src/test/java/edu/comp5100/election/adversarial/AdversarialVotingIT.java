package edu.comp5100.election.adversarial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.domain.AuditEvent;
import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.repository.AuditEventRepository;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.UserAdminService;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI-assisted adversarial suite, candidates C05, C14 and C18
 * (docs/ai-testing/03-candidate-decisions.md), against the real services and H2 database.
 */
@IntegrationTest
@Tag("adversarial")
@DisplayName("Adversarial: voting service with a real database")
class AdversarialVotingIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;
    private static final Instant END = BASE.plus(Duration.ofHours(1));

    @Autowired
    private VotingService votingService;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private ExecutorService threads;
    private Election election;
    private List<Candidate> candidates;
    private UserAccount admin;
    private UserAccount voter;

    @BeforeEach
    void setUp() {
        clock.setInstant(BASE);
        threads = Executors.newFixedThreadPool(4);
        election = data.openElection("Adversarial election", BASE.minus(Duration.ofHours(1)), END, "Yes", "No");
        candidates = data.candidatesOf(election);
        admin = data.admin("adv-admin");
        voter = data.voter("adv-voter");
        data.makeEligible(election, voter);
    }

    @AfterEach
    void stopThreads() {
        threads.shutdownNow();
    }

    private VoteRejectionReason rejectionOf(Runnable attempt) {
        try {
            attempt.run();
            return null;
        } catch (VoteRejectedException rejected) {
            return rejected.reason();
        }
    }

    @Test
    @DisplayName("FR-07/NFR-03 (C05): a vote sent before the deadline but processed after it is rejected")
    void deadlinePassesWhileTheVoteWaitsForTheLock() throws Exception {
        clock.setInstant(END.minusSeconds(1));
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Future<?> holder = threads.submit(() -> transaction.executeWithoutResult(status -> {
            electionRepository.findByIdForUpdate(election.getId());
            lockHeld.countDown();
            try {
                releaseLock.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(lockHeld.await(15, TimeUnit.SECONDS)).isTrue();

        // Sent one second before the deadline: it would be accepted if it were judged on arrival.
        Future<VoteRejectionReason> vote = threads.submit(() -> rejectionOf(
                () -> votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId())));
        assertThatThrownBy(() -> vote.get(400, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        clock.setInstant(END);
        releaseLock.countDown();
        holder.get(15, TimeUnit.SECONDS);

        assertThat(vote.get(15, TimeUnit.SECONDS)).isEqualTo(VoteRejectionReason.VOTING_ENDED);
        assertThat(participationRepository.countByElectionId(election.getId())).isZero();
        assertThat(ballotRepository.countByElectionId(election.getId())).isZero();
    }

    @Test
    @DisplayName("FR-02/FR-17 (C14): a disabled account is refused, and after re-enabling it can vote exactly once")
    void reEnabledAccountVotesExactlyOnce() {
        Long candidateId = candidates.get(0).getId();
        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), false);
        assertThat(rejectionOf(() -> votingService.castVote(voter.getUsername(), election.getId(), candidateId)))
                .isEqualTo(VoteRejectionReason.ACCOUNT_NOT_PERMITTED);

        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), true);
        assertThat(rejectionOf(() -> votingService.castVote(voter.getUsername(), election.getId(), candidateId)))
                .isNull();
        assertThat(rejectionOf(() -> votingService.castVote(voter.getUsername(), election.getId(), candidateId)))
                .isEqualTo(VoteRejectionReason.ALREADY_VOTED);

        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-15/NFR-01 (C18): the audit record of a foreign-candidate attempt does not store the candidate")
    void foreignCandidateAttemptIsAuditedWithoutTheCandidate() {
        Election other = data.openElection("Adversarial other", BASE.minus(Duration.ofHours(1)), END,
                "Foreign One", "Foreign Two");
        Candidate foreign = data.candidatesOf(other).get(1);

        assertThat(rejectionOf(() -> votingService.castVote(voter.getUsername(), election.getId(), foreign.getId())))
                .isEqualTo(VoteRejectionReason.INVALID_CANDIDATE);

        List<AuditEvent> events = auditEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.VOTE_REJECTED)
                .filter(event -> voter.getUsername().equals(event.getActor()))
                .toList();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.getElectionId()).isEqualTo(election.getId());
            assertThat(event.getDetail()).isEqualTo("Reason: INVALID_CANDIDATE (vote submission)");
            assertThat(event.getDetail()).doesNotContain(String.valueOf(foreign.getId()), foreign.getName());
        });
        assertThat(ballotRepository.countByElectionId(other.getId())).isZero();
    }
}
