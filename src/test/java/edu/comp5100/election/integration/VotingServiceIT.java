package edu.comp5100.election.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.domain.AuditEvent;
import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Ballot;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionEligibility;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.repository.AuditEventRepository;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.ElectionAdminService;
import edu.comp5100.election.service.ReceiptStatus;
import edu.comp5100.election.service.ReceiptVerificationService;
import edu.comp5100.election.service.UserAdminService;
import edu.comp5100.election.service.VoteReceipt;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Voting component integration tests: the real VotingService, VoteTransaction, AuditService,
 * Spring Data repositories, Flyway schema and H2 database. Nothing is mocked. Only the clock is
 * replaced by a controllable one.
 */
@IntegrationTest
@DisplayName("Voting with real services and database")
class VotingServiceIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;

    @Autowired
    private VotingService votingService;
    @Autowired
    private ElectionAdminService electionAdminService;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ReceiptVerificationService receiptVerificationService;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private ElectionEligibilityRepository eligibilityRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private Election election;
    private List<Candidate> candidates;
    private UserAccount voter;
    private UserAccount admin;

    @BeforeEach
    void openElectionWithOneEligibleVoter() {
        clock.setInstant(BASE);
        election = data.openElection("Council", BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)),
                "Jordan Lee", "Priya Nair", "Marcus Hill");
        candidates = data.candidatesOf(election);
        voter = data.voter("voter");
        admin = data.admin("admin");
        data.makeEligible(election, voter);
    }

    private long participations(Election target) {
        return participationRepository.countByElectionId(target.getId());
    }

    private long ballots(Election target) {
        return ballotRepository.countByElectionId(target.getId());
    }

    private List<AuditEvent> rejectionsOf(UserAccount account) {
        return auditEventRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.VOTE_REJECTED)
                .filter(event -> account.getUsername().equals(event.getActor()))
                .toList();
    }

    private static void assertRejected(ThrowingCallable attempt, VoteRejectionReason expected) {
        assertThatThrownBy(attempt).isInstanceOfSatisfying(VoteRejectedException.class,
                rejected -> assertThat(rejected.reason()).isEqualTo(expected));
    }

    private void assertNothingStored(Election target) {
        assertThat(participations(target)).isZero();
        assertThat(ballots(target)).isZero();
    }

    @Test
    @DisplayName("FR-09/FR-12: an accepted vote stores exactly one participation and one ballot and returns a code")
    void acceptedVoteIsStoredOnce() {
        VoteReceipt receipt = votingService.castVote(voter.getUsername(), election.getId(), candidates.get(1).getId());

        assertThat(receipt.code()).matches("([0-9A-HJKMNP-TV-Z]{4}-){7}[0-9A-HJKMNP-TV-Z]{4}");
        assertThat(participations(election)).isEqualTo(1);
        assertThat(ballots(election)).isEqualTo(1);
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(1).getId());
        assertThat(participationRepository.existsByElectionIdAndVoterId(election.getId(), voter.getId())).isTrue();
    }

    @Test
    @DisplayName("FR-13/NFR-01: the code verifies as accepted, and the database holds only its hash")
    void receiptVerifiesAndOnlyTheHashIsStored() {
        VoteReceipt receipt = votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        assertThat(receiptVerificationService.verify(receipt.code())).isEqualTo(ReceiptStatus.ACCEPTED);
        assertThat(receiptVerificationService.verify(receipt.code().toLowerCase())).isEqualTo(ReceiptStatus.ACCEPTED);
        assertThat(receiptVerificationService.verify("0000-0000-0000-0000-0000-0000-0000-0000"))
                .isEqualTo(ReceiptStatus.NOT_FOUND);
        assertThat(receiptVerificationService.verify("garbage")).isEqualTo(ReceiptStatus.MALFORMED);

        String canonical = ReceiptCodec.normalize(receipt.code()).orElseThrow();
        List<Ballot> stored = ballotRepository.findAll().stream()
                .filter(ballot -> ballot.getElectionId().equals(election.getId()))
                .toList();
        assertThat(stored).singleElement().satisfies(ballot -> {
            assertThat(ballot.getReceiptHash()).isEqualTo(ReceiptCodec.hash(canonical));
            assertThat(ballot.getReceiptHash()).doesNotContain(canonical);
        });
    }

    @Test
    @DisplayName("FR-10: a second vote by the same voter is rejected and changes nothing")
    void secondVoteIsRejected() {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), candidates.get(2).getId()),
                VoteRejectionReason.ALREADY_VOTED);

        assertThat(participations(election)).isEqualTo(1);
        assertThat(ballots(election)).isEqualTo(1);
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(0).getId());
    }

    @Test
    @DisplayName("FR-10: the same voter can vote in another election where they are eligible")
    void voterCanVoteInAnotherElection() {
        Election second = data.openElection("Referendum", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        data.makeEligible(second, voter);

        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());
        votingService.castVote(voter.getUsername(), second.getId(), data.candidatesOf(second).get(1).getId());

        assertThat(participations(election)).isEqualTo(1);
        assertThat(participations(second)).isEqualTo(1);
        assertThat(ballots(second)).isEqualTo(1);
    }

    @Test
    @DisplayName("NFR-11: the audit record of a rejected vote survives the rollback of the voting transaction")
    void rejectionAuditSurvivesRollback() {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());
        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), candidates.get(1).getId()),
                VoteRejectionReason.ALREADY_VOTED);

        assertThat(rejectionsOf(voter)).singleElement().satisfies(event -> {
            assertThat(event.getElectionId()).isEqualTo(election.getId());
            assertThat(event.getDetail()).isEqualTo("Reason: ALREADY_VOTED (vote submission)");
            assertThat(event.getOccurredAt()).isEqualTo(BASE);
        });
    }

    @Test
    @DisplayName("FR-15/NFR-01: an accepted vote leaves no audit record that names the voter")
    void acceptedVoteIsNotAuditedPerVoter() {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        assertThat(auditEventRepository.findAll())
                .noneMatch(event -> voter.getUsername().equals(event.getActor()))
                .noneMatch(event -> election.getId().equals(event.getElectionId()));
    }

    @Test
    @DisplayName("FR-15/NFR-01: the audit record of a rejection never names the candidate that was submitted")
    void rejectionAuditDoesNotContainTheChoice() {
        Candidate chosen = candidates.get(2);
        UserAccount outsider = data.voter("outsider");

        assertRejected(() -> votingService.castVote(outsider.getUsername(), election.getId(), chosen.getId()),
                VoteRejectionReason.NOT_ELIGIBLE);

        assertThat(rejectionsOf(outsider)).singleElement().satisfies(event -> {
            assertThat(event.getDetail()).doesNotContain(chosen.getName());
            assertThat(event.getDetail()).doesNotContain(String.valueOf(chosen.getId()));
        });
    }

    @Test
    @DisplayName("FR-05: a voter without eligibility is rejected and nothing is stored")
    void ineligibleVoterIsRejected() {
        UserAccount outsider = data.voter("outsider");

        assertRejected(() -> votingService.castVote(outsider.getUsername(), election.getId(),
                candidates.get(0).getId()), VoteRejectionReason.NOT_ELIGIBLE);

        assertNothingStored(election);
        assertThat(rejectionsOf(outsider)).hasSize(1);
    }

    @Test
    @DisplayName("FR-05: a voter whose eligibility was revoked is rejected")
    void revokedEligibilityIsRejected() {
        electionAdminService.revokeEligibility(admin.getUsername(), election.getId(), voter.getId());

        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId()),
                VoteRejectionReason.NOT_ELIGIBLE);
        assertNothingStored(election);
    }

    @Test
    @DisplayName("FR-17: a disabled account is rejected by the service, whatever the session says")
    void disabledAccountIsRejected() {
        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), false);

        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId()),
                VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
        assertNothingStored(election);
    }

    @Test
    @DisplayName("FR-16: an administrator cannot vote, even with an eligibility row forced into the database")
    void administratorCannotVote() {
        eligibilityRepository.save(new ElectionEligibility(election.getId(), admin.getId()));

        assertRejected(() -> votingService.castVote(admin.getUsername(), election.getId(), candidates.get(0).getId()),
                VoteRejectionReason.ACCOUNT_NOT_PERMITTED);
        assertNothingStored(election);
    }

    @Test
    @DisplayName("FR-11: a candidate of another election is rejected and neither election stores anything")
    void candidateOfAnotherElectionIsRejected() {
        Election second = data.openElection("Referendum", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Yes", "No");
        data.makeEligible(second, voter);
        Long foreignCandidate = data.candidatesOf(second).get(0).getId();

        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), foreignCandidate),
                VoteRejectionReason.INVALID_CANDIDATE);

        assertNothingStored(election);
        assertNothingStored(second);
    }

    @Test
    @DisplayName("FR-11: a missing or non-existent candidate is rejected")
    void missingOrUnknownCandidateIsRejected() {
        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), null),
                VoteRejectionReason.NO_CANDIDATE_SELECTED);
        assertRejected(() -> votingService.castVote(voter.getUsername(), election.getId(), 9_999_999L),
                VoteRejectionReason.INVALID_CANDIDATE);
        assertNothingStored(election);
    }

    @Test
    @DisplayName("FR-11: an unknown election id is rejected and audited without an election reference")
    void unknownElectionIsRejected() {
        assertRejected(() -> votingService.castVote(voter.getUsername(), 9_999_999L, candidates.get(0).getId()),
                VoteRejectionReason.ELECTION_NOT_FOUND);

        assertThat(rejectionsOf(voter)).singleElement().satisfies(event -> {
            assertThat(event.getElectionId()).isNull();
            assertThat(event.getDetail()).contains("ELECTION_NOT_FOUND");
        });
    }

    @Test
    @DisplayName("FR-07: votes are accepted from the start instant up to, but not including, the end instant")
    void votingWindowIsEnforcedByTheServerClock() {
        Election timed = data.openElection("Timed", BASE, BASE.plus(Duration.ofHours(1)), "Yes", "No");
        Long yes = data.candidatesOf(timed).get(0).getId();
        UserAccount early = data.voter("early");
        UserAccount atStart = data.voter("at-start");
        UserAccount lastMoment = data.voter("last-moment");
        UserAccount atEnd = data.voter("at-end");
        data.makeEligible(timed, early, atStart, lastMoment, atEnd);

        clock.setInstant(BASE.minusNanos(1));
        assertRejected(() -> votingService.castVote(early.getUsername(), timed.getId(), yes),
                VoteRejectionReason.VOTING_NOT_STARTED);

        clock.setInstant(BASE);
        votingService.castVote(atStart.getUsername(), timed.getId(), yes);

        clock.setInstant(BASE.plus(Duration.ofHours(1)).minusNanos(1));
        votingService.castVote(lastMoment.getUsername(), timed.getId(), yes);

        clock.setInstant(BASE.plus(Duration.ofHours(1)));
        assertRejected(() -> votingService.castVote(atEnd.getUsername(), timed.getId(), yes),
                VoteRejectionReason.VOTING_ENDED);

        assertThat(participations(timed)).isEqualTo(2);
        assertThat(ballots(timed)).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-06: a draft election accepts no votes")
    void draftElectionAcceptsNoVotes() {
        Election draft = data.draftElection("Draft", BASE.minus(Duration.ofHours(1)), BASE.plus(Duration.ofHours(1)),
                "Yes", "No");
        data.makeEligible(draft, voter);

        assertRejected(() -> votingService.castVote(voter.getUsername(), draft.getId(),
                data.candidatesOf(draft).get(0).getId()), VoteRejectionReason.ELECTION_NOT_OPEN);
        assertNothingStored(draft);
    }

    @Test
    @DisplayName("FR-06: after closing, new votes are rejected and earlier votes stay counted")
    void closingStopsVotingAndKeepsEarlierVotes() {
        UserAccount late = data.voter("late");
        data.makeEligible(election, late);
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        electionAdminService.close(admin.getUsername(), election.getId());

        assertRejected(() -> votingService.castVote(late.getUsername(), election.getId(), candidates.get(1).getId()),
                VoteRejectionReason.ELECTION_CLOSED);
        assertThat(participations(election)).isEqualTo(1);
        assertThat(ballots(election)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-09/FR-15: the ballot opens for an eligible voter; after voting the request is refused and audited")
    void ballotRequestAfterVotingIsRefusedAndAudited() {
        assertThat(votingService.openBallot(voter.getUsername(), election.getId()).candidates())
                .extracting(Candidate::getName).containsExactly("Jordan Lee", "Priya Nair", "Marcus Hill");

        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(0).getId());

        assertRejected(() -> votingService.openBallot(voter.getUsername(), election.getId()),
                VoteRejectionReason.ALREADY_VOTED);
        assertThat(rejectionsOf(voter)).singleElement()
                .satisfies(event -> assertThat(event.getDetail()).isEqualTo("Reason: ALREADY_VOTED (ballot request)"));
    }

    @Test
    @DisplayName("FR-05/FR-02: revoking eligibility or disabling the account never removes an accepted vote")
    void accountManagementDoesNotChangeAcceptedVotes() {
        votingService.castVote(voter.getUsername(), election.getId(), candidates.get(1).getId());

        electionAdminService.revokeEligibility(admin.getUsername(), election.getId(), voter.getId());
        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), false);

        assertThat(participations(election)).isEqualTo(1);
        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId()))
                .containsExactly(candidates.get(1).getId());
    }
}
