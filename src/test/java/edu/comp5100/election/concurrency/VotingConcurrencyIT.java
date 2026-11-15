package edu.comp5100.election.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.ElectionAdminService;
import edu.comp5100.election.service.ReceiptStatus;
import edu.comp5100.election.service.ReceiptVerificationService;
import edu.comp5100.election.service.UserAdminService;
import edu.comp5100.election.service.VoteNotProcessedException;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency tests against the real services and a real H2 database (NFR-02, NFR-03). Every
 * thread calls the service directly and therefore runs in its own database transaction; the test
 * methods themselves are not transactional. Races are forced with barriers and latches, never
 * with sleeps. A latch is counted down only after the holding transaction has taken its lock,
 * and it is awaited by a different thread, so the tests cannot deadlock on their own latches.
 *
 * <p>These tests show the behaviour on H2 with its row locks. They do not prove the same
 * behaviour on another database engine.
 */
@IntegrationTest
@DisplayName("Concurrent voting, closing and account changes (real transactions, H2)")
class VotingConcurrencyIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;
    private static final long WAIT_SECONDS = 15;
    /** How long a blocked operation is observed before the test concludes that it is waiting for a lock. */
    private static final long BLOCK_OBSERVATION_MILLIS = 400;

    /** What one voting thread saw. Exactly one of the three fields is set. */
    private record Outcome(String receiptCode, VoteRejectionReason rejection, boolean notProcessed) {
        boolean accepted() {
            return receiptCode != null;
        }
    }

    @Autowired
    private VotingService votingService;
    @Autowired
    private ElectionAdminService electionAdminService;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ReceiptVerificationService receiptVerificationService;
    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private ExecutorService threads;
    private TransactionTemplate transaction;
    private UserAccount admin;
    private Election election;
    private List<Candidate> candidates;

    @BeforeEach
    void setUp() {
        clock.setInstant(BASE);
        threads = Executors.newFixedThreadPool(24);
        transaction = new TransactionTemplate(transactionManager);
        admin = data.admin("race-admin");
        election = data.openElection("Race election", BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)),
                "Jordan Lee", "Priya Nair", "Marcus Hill");
        candidates = data.candidatesOf(election);
    }

    @AfterEach
    void stopThreads() {
        threads.shutdownNow();
    }

    private Outcome attempt(UserAccount voter, Election target, Long candidateId) {
        try {
            return new Outcome(votingService.castVote(voter.getUsername(), target.getId(), candidateId).code(), null,
                    false);
        } catch (VoteRejectedException rejected) {
            return new Outcome(null, rejected.reason(), false);
        } catch (VoteNotProcessedException notProcessed) {
            return new Outcome(null, null, true);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).as("latch released in time").isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private static <T> T result(Future<T> future) throws Exception {
        return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(BLOCK_OBSERVATION_MILLIS, TimeUnit.MILLISECONDS))
                .as("the operation must wait for the lock held by the other transaction")
                .isInstanceOf(TimeoutException.class);
    }

    private long participations() {
        return participationRepository.countByElectionId(election.getId());
    }

    private long ballots() {
        return ballotRepository.countByElectionId(election.getId());
    }

    @Test
    @DisplayName("FR-10/NFR-02: ten simultaneous votes of one voter give exactly one accepted vote and nine duplicates")
    void simultaneousVotesOfOneVoter() throws Exception {
        UserAccount voter = data.voter("race-voter");
        data.makeEligible(election, voter);
        int attempts = 10;
        CyclicBarrier startTogether = new CyclicBarrier(attempts);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (int index = 0; index < attempts; index++) {
            Long candidateId = candidates.get(index % candidates.size()).getId();
            futures.add(threads.submit(() -> {
                startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
                return attempt(voter, election, candidateId);
            }));
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(result(future));
        }

        assertThat(outcomes).filteredOn(Outcome::accepted).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.rejection() == VoteRejectionReason.ALREADY_VOTED)
                .hasSize(attempts - 1);
        assertThat(participations()).isEqualTo(1);
        assertThat(ballots()).isEqualTo(1);
        String code = outcomes.stream().filter(Outcome::accepted).findFirst().orElseThrow().receiptCode();
        assertThat(receiptVerificationService.verify(code)).isEqualTo(ReceiptStatus.ACCEPTED);
    }

    @Test
    @DisplayName("NFR-02: twenty different voters voting at once lose no vote and create no extra vote")
    void simultaneousVotesOfDifferentVoters() throws Exception {
        int voterCount = 20;
        List<UserAccount> voters = new ArrayList<>();
        for (int index = 0; index < voterCount; index++) {
            voters.add(data.voter("race-crowd"));
        }
        data.makeEligible(election, voters.toArray(UserAccount[]::new));
        CyclicBarrier startTogether = new CyclicBarrier(voterCount);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (int index = 0; index < voterCount; index++) {
            UserAccount voter = voters.get(index);
            Long candidateId = candidates.get(index % candidates.size()).getId();
            futures.add(threads.submit(() -> {
                startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
                return attempt(voter, election, candidateId);
            }));
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(result(future));
        }

        assertThat(outcomes).allMatch(Outcome::accepted);
        assertThat(outcomes).extracting(Outcome::receiptCode).doesNotHaveDuplicates();
        assertThat(participations()).isEqualTo(voterCount);
        assertThat(ballots()).isEqualTo(voterCount);
        List<Long> stored = ballotRepository.findCandidateIdsByElectionId(election.getId());
        assertThat(Collections.frequency(stored, candidates.get(0).getId())).isEqualTo(7);
        assertThat(Collections.frequency(stored, candidates.get(1).getId())).isEqualTo(7);
        assertThat(Collections.frequency(stored, candidates.get(2).getId())).isEqualTo(6);
        for (Outcome outcome : outcomes) {
            assertThat(receiptVerificationService.verify(outcome.receiptCode())).isEqualTo(ReceiptStatus.ACCEPTED);
        }
    }

    @Test
    @DisplayName("FR-10: one voter voting in two elections at the same moment is accepted in both")
    void oneVoterInTwoElectionsAtOnce() throws Exception {
        Election second = data.openElection("Second race", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        UserAccount voter = data.voter("race-two");
        data.makeEligible(election, voter);
        data.makeEligible(second, voter);
        CyclicBarrier startTogether = new CyclicBarrier(2);

        Future<Outcome> first = threads.submit(() -> {
            startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return attempt(voter, election, candidates.get(0).getId());
        });
        Future<Outcome> other = threads.submit(() -> {
            startTogether.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return attempt(voter, second, data.candidatesOf(second).get(0).getId());
        });

        assertThat(result(first).accepted()).isTrue();
        assertThat(result(other).accepted()).isTrue();
        assertThat(ballots()).isEqualTo(1);
        assertThat(ballotRepository.countByElectionId(second.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-06/NFR-03: a vote that waits behind a closing transaction is rejected once the close commits")
    void closeFirstThenWaitingVoteIsRejected() throws Exception {
        UserAccount voter = data.voter("race-late");
        data.makeEligible(election, voter);
        CountDownLatch closeHoldsLock = new CountDownLatch(1);
        CountDownLatch letCloseCommit = new CountDownLatch(1);

        Future<?> closing = threads.submit(() -> transaction.executeWithoutResult(status -> {
            electionAdminService.close(admin.getUsername(), election.getId());
            closeHoldsLock.countDown();
            await(letCloseCommit);
        }));
        await(closeHoldsLock);
        Future<Outcome> vote = threads.submit(() -> attempt(voter, election, candidates.get(0).getId()));

        assertBlocked(vote);
        letCloseCommit.countDown();
        result(closing);

        assertThat(result(vote).rejection()).isEqualTo(VoteRejectionReason.ELECTION_CLOSED);
        assertThat(participations()).isZero();
        assertThat(ballots()).isZero();
        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.CLOSED);
    }

    @Test
    @DisplayName("FR-06/NFR-03: a vote that takes the lock before the close is accepted; the close waits, then wins")
    void voteFirstThenWaitingCloseSucceeds() throws Exception {
        UserAccount voter = data.voter("race-early");
        UserAccount afterClose = data.voter("race-after");
        data.makeEligible(election, voter, afterClose);
        CountDownLatch voteHoldsLock = new CountDownLatch(1);
        CountDownLatch letVoteCommit = new CountDownLatch(1);

        Future<Outcome> vote = threads.submit(() -> transaction.execute(status -> {
            Outcome outcome = attempt(voter, election, candidates.get(1).getId());
            voteHoldsLock.countDown();
            await(letVoteCommit);
            return outcome;
        }));
        await(voteHoldsLock);
        Future<?> closing = threads.submit(() -> electionAdminService.close(admin.getUsername(), election.getId()));

        assertBlocked(closing);
        letVoteCommit.countDown();

        assertThat(result(vote).accepted()).isTrue();
        result(closing);
        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.CLOSED);
        assertThat(participations()).isEqualTo(1);
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(1).getId());
        assertThat(attempt(afterClose, election, candidates.get(0).getId()).rejection())
                .isEqualTo(VoteRejectionReason.ELECTION_CLOSED);
        assertThat(ballots()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-17/NFR-03: a vote that waits behind an account being disabled is rejected after the commit")
    void disableFirstThenWaitingVoteIsRejected() throws Exception {
        UserAccount voter = data.voter("race-disabled");
        data.makeEligible(election, voter);
        CountDownLatch disableHoldsLock = new CountDownLatch(1);
        CountDownLatch letDisableCommit = new CountDownLatch(1);

        Future<?> disabling = threads.submit(() -> transaction.executeWithoutResult(status -> {
            userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), false);
            disableHoldsLock.countDown();
            await(letDisableCommit);
        }));
        await(disableHoldsLock);
        Future<Outcome> vote = threads.submit(() -> attempt(voter, election, candidates.get(0).getId()));

        assertBlocked(vote);
        letDisableCommit.countDown();
        result(disabling);

        assertThat(result(vote).rejection()).isEqualTo(VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
        assertThat(participations()).isZero();
        assertThat(ballots()).isZero();
    }

    @Test
    @DisplayName("FR-05/NFR-03: a vote that waits behind an eligibility removal is rejected after the commit")
    void revokeFirstThenWaitingVoteIsRejected() throws Exception {
        UserAccount voter = data.voter("race-revoked");
        data.makeEligible(election, voter);
        CountDownLatch revokeHoldsLock = new CountDownLatch(1);
        CountDownLatch letRevokeCommit = new CountDownLatch(1);

        Future<?> revoking = threads.submit(() -> transaction.executeWithoutResult(status -> {
            electionAdminService.revokeEligibility(admin.getUsername(), election.getId(), voter.getId());
            revokeHoldsLock.countDown();
            await(letRevokeCommit);
        }));
        await(revokeHoldsLock);
        Future<Outcome> vote = threads.submit(() -> attempt(voter, election, candidates.get(0).getId()));

        assertBlocked(vote);
        letRevokeCommit.countDown();
        result(revoking);

        assertThat(result(vote).rejection()).isEqualTo(VoteRejectionReason.NOT_ELIGIBLE);
        assertThat(participations()).isZero();
        assertThat(ballots()).isZero();
    }
}
