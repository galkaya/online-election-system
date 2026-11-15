package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.comp5100.election.domain.Ballot;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.Participation;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import edu.comp5100.election.service.BallotView;
import edu.comp5100.election.service.ReceiptGenerator;
import edu.comp5100.election.service.VoteReceipt;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VoteTransaction;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the voting transaction logic (FR-09 to FR-12, NFR-01, NFR-02). Repositories are
 * mocked because they are the boundary to the database; transaction and lock behaviour with a
 * real database is tested in the integration and concurrency suites.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Vote transaction (repositories mocked)")
class VoteTransactionTest {

    private static final long ELECTION_ID = 5L;
    private static final long VOTER_ID = 7L;
    private static final long CANDIDATE_ID = 31L;
    private static final String USERNAME = "voter1";
    private static final String CODE = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final Instant START = Instant.parse("2026-11-19T15:00:00Z");
    private static final Instant END = Instant.parse("2026-11-20T15:00:00Z");

    @Mock
    private ElectionRepository electionRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private ElectionEligibilityRepository eligibilityRepository;
    @Mock
    private ParticipationRepository participationRepository;
    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private BallotRepository ballotRepository;
    @Mock
    private ReceiptGenerator receiptGenerator;

    private Election election;
    private UserAccount voter;

    @BeforeEach
    void everythingAllowsTheVote() {
        election = new Election("Council", "Test election", START, END, START);
        ReflectionTestUtils.setField(election, "id", ELECTION_ID);
        election.markOpen();
        voter = new UserAccount(USERNAME, "{noop}secret", "Ava Brooks", Role.VOTER, START);
        ReflectionTestUtils.setField(voter, "id", VOTER_ID);

        when(electionRepository.findByIdForUpdate(ELECTION_ID)).thenReturn(Optional.of(election));
        when(electionRepository.findById(ELECTION_ID)).thenReturn(Optional.of(election));
        when(userAccountRepository.findByUsernameForUpdate(USERNAME)).thenReturn(Optional.of(voter));
        when(userAccountRepository.findByUsername(USERNAME)).thenReturn(Optional.of(voter));
        when(eligibilityRepository.existsByElectionIdAndVoterIdAndActiveTrue(ELECTION_ID, VOTER_ID)).thenReturn(true);
        when(participationRepository.existsByElectionIdAndVoterId(ELECTION_ID, VOTER_ID)).thenReturn(false);
        when(candidateRepository.existsByIdAndElectionId(CANDIDATE_ID, ELECTION_ID)).thenReturn(true);
        when(receiptGenerator.newCode()).thenReturn(CODE);
    }

    private VoteTransaction transactionAt(Instant now) {
        return new VoteTransaction(electionRepository, userAccountRepository, eligibilityRepository,
                participationRepository, candidateRepository, ballotRepository, receiptGenerator,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private void assertRejected(VoteTransaction transaction, Long candidateId, VoteRejectionReason expected) {
        assertThatThrownBy(() -> transaction.castVote(USERNAME, ELECTION_ID, candidateId))
                .isInstanceOfSatisfying(VoteRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(expected));
        verify(participationRepository, never()).saveAndFlush(any());
        verify(ballotRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("FR-09/FR-12: an accepted vote stores the participation, then the ballot, and returns the code")
    void acceptedVoteStoresParticipationThenBallot() {
        VoteReceipt receipt = transactionAt(START.plusSeconds(60)).castVote(USERNAME, ELECTION_ID, CANDIDATE_ID);

        assertThat(receipt.code()).isEqualTo(ReceiptCodec.format(CODE));
        ArgumentCaptor<Participation> participation = ArgumentCaptor.forClass(Participation.class);
        ArgumentCaptor<Ballot> ballot = ArgumentCaptor.forClass(Ballot.class);
        InOrder order = inOrder(participationRepository, ballotRepository);
        order.verify(participationRepository).saveAndFlush(participation.capture());
        order.verify(ballotRepository).saveAndFlush(ballot.capture());

        assertThat(participation.getValue().getElectionId()).isEqualTo(ELECTION_ID);
        assertThat(participation.getValue().getVoterId()).isEqualTo(VOTER_ID);
        assertThat(ballot.getValue().getElectionId()).isEqualTo(ELECTION_ID);
        assertThat(ballot.getValue().getCandidateId()).isEqualTo(CANDIDATE_ID);
    }

    @Test
    @DisplayName("NFR-01: the ballot stores only the hash of the confirmation code, never the code itself")
    void ballotStoresOnlyTheReceiptHash() {
        transactionAt(START).castVote(USERNAME, ELECTION_ID, CANDIDATE_ID);

        ArgumentCaptor<Ballot> ballot = ArgumentCaptor.forClass(Ballot.class);
        verify(ballotRepository).saveAndFlush(ballot.capture());
        assertThat(ballot.getValue().getReceiptHash()).isEqualTo(ReceiptCodec.hash(CODE)).isNotEqualTo(CODE);
    }

    @Test
    @DisplayName("FR-10: a voter with a participation record is refused and nothing is written")
    void duplicateVoteWritesNothing() {
        when(participationRepository.existsByElectionIdAndVoterId(ELECTION_ID, VOTER_ID)).thenReturn(true);

        assertRejected(transactionAt(START), CANDIDATE_ID, VoteRejectionReason.ALREADY_VOTED);
        verifyNoInteractions(receiptGenerator);
    }

    @Test
    @DisplayName("NFR-02: a unique-constraint failure while storing the participation is a duplicate, and no ballot is stored")
    void constraintViolationIsTreatedAsDuplicate() {
        when(participationRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> transactionAt(START).castVote(USERNAME, ELECTION_ID, CANDIDATE_ID))
                .isInstanceOfSatisfying(VoteRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.ALREADY_VOTED));
        verify(ballotRepository, never()).saveAndFlush(any());
        verifyNoInteractions(receiptGenerator);
    }

    @Test
    @DisplayName("FR-11: an unknown election id is refused before anything else is looked at")
    void unknownElectionIsRefused() {
        when(electionRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionAt(START).castVote(USERNAME, 404L, CANDIDATE_ID))
                .isInstanceOfSatisfying(VoteRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.ELECTION_NOT_FOUND));
        verifyNoInteractions(userAccountRepository, participationRepository, ballotRepository);
    }

    @Test
    @DisplayName("FR-16: an unknown account is refused")
    void unknownAccountIsRefused() {
        when(userAccountRepository.findByUsernameForUpdate(USERNAME)).thenReturn(Optional.empty());

        assertRejected(transactionAt(START), CANDIDATE_ID, VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
    }

    @Test
    @DisplayName("FR-17: a disabled voter account is refused even though it is eligible")
    void disabledAccountIsRefused() {
        voter.setEnabled(false);

        assertRejected(transactionAt(START), CANDIDATE_ID, VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
    }

    @Test
    @DisplayName("FR-16: an administrator account cannot vote, even with an eligibility record")
    void administratorCannotVote() {
        UserAccount admin = new UserAccount(USERNAME, "{noop}secret", "Admin", Role.ADMIN, START);
        ReflectionTestUtils.setField(admin, "id", VOTER_ID);
        when(userAccountRepository.findByUsernameForUpdate(USERNAME)).thenReturn(Optional.of(admin));

        assertRejected(transactionAt(START), CANDIDATE_ID, VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
    }

    @Test
    @DisplayName("FR-05: a voter without active eligibility is refused")
    void ineligibleVoterIsRefused() {
        when(eligibilityRepository.existsByElectionIdAndVoterIdAndActiveTrue(ELECTION_ID, VOTER_ID)).thenReturn(false);

        assertRejected(transactionAt(START), CANDIDATE_ID, VoteRejectionReason.NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("FR-07: the service uses the injected clock: accepted at the start, refused at the end instant")
    void clockDecidesTheWindow() {
        assertRejected(transactionAt(START.minusSeconds(1)), CANDIDATE_ID, VoteRejectionReason.VOTING_NOT_STARTED);
        assertRejected(transactionAt(END), CANDIDATE_ID, VoteRejectionReason.VOTING_ENDED);

        assertThat(transactionAt(END.minusSeconds(1)).castVote(USERNAME, ELECTION_ID, CANDIDATE_ID)).isNotNull();
    }

    @Test
    @DisplayName("FR-06: a closed election refuses votes even inside its time window")
    void closedElectionIsRefused() {
        election.markClosed();

        assertRejected(transactionAt(START.plusSeconds(60)), CANDIDATE_ID, VoteRejectionReason.ELECTION_CLOSED);
    }

    @Test
    @DisplayName("FR-11: a vote without a candidate is refused without a candidate lookup")
    void missingCandidateIsRefused() {
        assertRejected(transactionAt(START), null, VoteRejectionReason.NO_CANDIDATE_SELECTED);
        verify(candidateRepository, never()).existsByIdAndElectionId(any(), anyLong());
    }

    @Test
    @DisplayName("FR-11: a candidate id that belongs to another election is refused")
    void candidateOfAnotherElectionIsRefused() {
        when(candidateRepository.existsByIdAndElectionId(77L, ELECTION_ID)).thenReturn(false);

        assertRejected(transactionAt(START), 77L, VoteRejectionReason.INVALID_CANDIDATE);
    }

    @Test
    @DisplayName("FR-09: the ballot lists the candidates of the election for a voter who may vote")
    void ballotIsLoadedForAnAllowedVoter() {
        Candidate jordan = new Candidate(ELECTION_ID, "Jordan Lee", "Statement");
        ReflectionTestUtils.setField(jordan, "id", CANDIDATE_ID);
        when(candidateRepository.findByElectionIdOrderByIdAsc(ELECTION_ID)).thenReturn(List.of(jordan));

        BallotView ballot = transactionAt(START).loadBallot(USERNAME, ELECTION_ID);

        assertThat(ballot.election()).isSameAs(election);
        assertThat(ballot.candidates()).containsExactly(jordan);
        assertThat(ballot.candidate(CANDIDATE_ID)).isSameAs(jordan);
        assertThat(ballot.candidate(999L)).isNull();
    }

    @Test
    @DisplayName("FR-10: the ballot is not shown to a voter who has already voted")
    void ballotIsRefusedAfterVoting() {
        when(participationRepository.existsByElectionIdAndVoterId(ELECTION_ID, VOTER_ID)).thenReturn(true);

        assertThatThrownBy(() -> transactionAt(START).loadBallot(USERNAME, ELECTION_ID))
                .isInstanceOfSatisfying(VoteRejectedException.class,
                        rejected -> assertThat(rejected.reason()).isEqualTo(VoteRejectionReason.ALREADY_VOTED));
    }
}
