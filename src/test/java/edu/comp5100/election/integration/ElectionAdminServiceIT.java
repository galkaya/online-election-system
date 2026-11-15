package edu.comp5100.election.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.domain.AuditEvent;
import edu.comp5100.election.domain.AuditEventType;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.CandidateTally;
import edu.comp5100.election.policy.ElectionPhase;
import edu.comp5100.election.policy.TallyOutcome;
import edu.comp5100.election.repository.AuditEventRepository;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import edu.comp5100.election.service.BusinessRuleException;
import edu.comp5100.election.service.ElectionAdminDetail;
import edu.comp5100.election.service.ElectionAdminService;
import edu.comp5100.election.service.ElectionOverview;
import edu.comp5100.election.service.ElectionQueryService;
import edu.comp5100.election.service.ElectionResults;
import edu.comp5100.election.service.NotFoundException;
import edu.comp5100.election.service.ResultsNotAvailableException;
import edu.comp5100.election.service.UserAdminService;
import edu.comp5100.election.service.VoterElectionRow;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.IntegrationTest;
import edu.comp5100.election.support.MutableClock;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Election administration integration tests: real services, repositories, schema and H2 database.
 * Nothing is mocked; only the clock is controllable.
 */
@IntegrationTest
@DisplayName("Election administration with real services and database")
class ElectionAdminServiceIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;
    private static final Instant START = BASE.minus(Duration.ofHours(1));
    private static final Instant END = BASE.plus(Duration.ofHours(1));

    @Autowired
    private ElectionAdminService adminService;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private ElectionQueryService queryService;
    @Autowired
    private VotingService votingService;
    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ElectionEligibilityRepository eligibilityRepository;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TestData data;
    @Autowired
    private MutableClock clock;

    private UserAccount admin;

    @BeforeEach
    void resetClock() {
        clock.setInstant(BASE);
        admin = data.admin("admin");
    }

    private Election reload(Election election) {
        return electionRepository.findById(election.getId()).orElseThrow();
    }

    private List<AuditEvent> eventsOf(Election election, AuditEventType type) {
        return auditEventRepository.findAll().stream()
                .filter(event -> election.getId().equals(event.getElectionId()) && event.getEventType() == type)
                .toList();
    }

    private Election activatedElection(String... candidateNames) {
        Election election = adminService.createElection(admin.getUsername(), "Council", "Description", START, END);
        for (String name : candidateNames) {
            adminService.addCandidate(admin.getUsername(), election.getId(), name, "Statement");
        }
        adminService.activate(admin.getUsername(), election.getId());
        return election;
    }

    @Test
    @DisplayName("FR-03/FR-15: a new election is a draft and its creation is audited with the administrator")
    void createdElectionIsAnAuditedDraft() {
        Election election = adminService.createElection(admin.getUsername(), "  Council  ", null, START, END);

        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.DRAFT);
        assertThat(reload(election).getTitle()).isEqualTo("Council");
        assertThat(reload(election).getDescription()).isEmpty();
        assertThat(eventsOf(election, AuditEventType.ELECTION_CREATED)).singleElement()
                .satisfies(event -> assertThat(event.getActor()).isEqualTo(admin.getUsername()));
    }

    @Test
    @DisplayName("FR-03: an election needs a title and a start strictly before the end")
    void invalidElectionDataIsRefused() {
        long before = electionRepository.count();

        assertThatThrownBy(() -> adminService.createElection(admin.getUsername(), "Council", "", END, START))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The start time must be before the end time.");
        assertThatThrownBy(() -> adminService.createElection(admin.getUsername(), "Council", "", START, START))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> adminService.createElection(admin.getUsername(), "   ", "", START, END))
                .isInstanceOf(BusinessRuleException.class).hasMessage("The title is required.");
        assertThatThrownBy(() -> adminService.createElection(admin.getUsername(), "x".repeat(121), "", START, END))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(electionRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("FR-06: activation needs at least two candidates")
    void activationNeedsTwoCandidates() {
        Election election = adminService.createElection(admin.getUsername(), "Council", "", START, END);
        adminService.addCandidate(admin.getUsername(), election.getId(), "Jordan Lee", "");

        assertThatThrownBy(() -> adminService.activate(admin.getUsername(), election.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("At least two candidates");
        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.DRAFT);

        adminService.addCandidate(admin.getUsername(), election.getId(), "Priya Nair", "");
        adminService.activate(admin.getUsername(), election.getId());

        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.OPEN);
        assertThat(eventsOf(election, AuditEventType.ELECTION_ACTIVATED)).hasSize(1);
    }

    @Test
    @DisplayName("FR-06: a draft whose deadline has passed cannot be activated")
    void expiredDraftCannotBeActivated() {
        Election election = adminService.createElection(admin.getUsername(), "Council", "", START, END);
        adminService.addCandidate(admin.getUsername(), election.getId(), "Jordan Lee", "");
        adminService.addCandidate(admin.getUsername(), election.getId(), "Priya Nair", "");
        clock.setInstant(END);

        assertThatThrownBy(() -> adminService.activate(admin.getUsername(), election.getId()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end time has already passed");
        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.DRAFT);
    }

    @Test
    @DisplayName("FR-06: after activation the title, schedule and candidates are locked")
    void setupIsLockedAfterActivation() {
        Election election = activatedElection("Jordan Lee", "Priya Nair");
        Candidate first = data.candidatesOf(election).get(0);

        assertThatThrownBy(() -> adminService.updateDraft(admin.getUsername(), election.getId(), "Renamed", "",
                START, END.plus(Duration.ofDays(1)))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> adminService.addCandidate(admin.getUsername(), election.getId(), "Marcus Hill", ""))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> adminService.removeCandidate(admin.getUsername(), election.getId(), first.getId()))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(reload(election).getTitle()).isEqualTo("Council");
        assertThat(reload(election).getEndsAt()).isEqualTo(END);
        assertThat(data.candidatesOf(election)).extracting(Candidate::getName)
                .containsExactly("Jordan Lee", "Priya Nair");
    }

    @Test
    @DisplayName("FR-03/FR-04: a draft can be edited and its candidates changed")
    void draftCanBeEdited() {
        Election election = adminService.createElection(admin.getUsername(), "Council", "Old", START, END);
        Candidate removed = adminService.addCandidate(admin.getUsername(), election.getId(), "Jordan Lee", "");
        adminService.addCandidate(admin.getUsername(), election.getId(), "Priya Nair", "");

        adminService.updateDraft(admin.getUsername(), election.getId(), "Council 2026", "New", START,
                END.plus(Duration.ofDays(2)));
        adminService.removeCandidate(admin.getUsername(), election.getId(), removed.getId());

        assertThat(reload(election).getTitle()).isEqualTo("Council 2026");
        assertThat(reload(election).getEndsAt()).isEqualTo(END.plus(Duration.ofDays(2)));
        assertThat(data.candidatesOf(election)).extracting(Candidate::getName).containsExactly("Priya Nair");
        assertThat(eventsOf(election, AuditEventType.CANDIDATE_REMOVED)).hasSize(1);
    }

    @Test
    @DisplayName("FR-04: candidate names must be present and unique within the election, ignoring case")
    void candidateNamesAreValidated() {
        Election election = adminService.createElection(admin.getUsername(), "Council", "", START, END);
        adminService.addCandidate(admin.getUsername(), election.getId(), "Jordan Lee", "");

        assertThatThrownBy(() -> adminService.addCandidate(admin.getUsername(), election.getId(), "jordan lee", ""))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("already has a candidate");
        assertThatThrownBy(() -> adminService.addCandidate(admin.getUsername(), election.getId(), "  ", ""))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(data.candidatesOf(election)).hasSize(1);
    }

    @Test
    @DisplayName("FR-04: a candidate cannot be removed through another election")
    void candidateRemovalIsScopedToItsElection() {
        Election first = adminService.createElection(admin.getUsername(), "First", "", START, END);
        Election second = adminService.createElection(admin.getUsername(), "Second", "", START, END);
        Candidate ofSecond = adminService.addCandidate(admin.getUsername(), second.getId(), "Priya Nair", "");

        assertThatThrownBy(() -> adminService.removeCandidate(admin.getUsername(), first.getId(), ofSecond.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(data.candidatesOf(second)).hasSize(1);
    }

    @Test
    @DisplayName("FR-06: only an activated election can be closed, once, and a closed election is never reopened")
    void closeRules() {
        Election draft = adminService.createElection(admin.getUsername(), "Draft", "", START, END);
        assertThatThrownBy(() -> adminService.close(admin.getUsername(), draft.getId()))
                .isInstanceOf(BusinessRuleException.class);

        Election election = activatedElection("Jordan Lee", "Priya Nair");
        adminService.close(admin.getUsername(), election.getId());
        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.CLOSED);
        assertThat(eventsOf(election, AuditEventType.ELECTION_CLOSED)).hasSize(1);

        assertThatThrownBy(() -> adminService.close(admin.getUsername(), election.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> adminService.activate(admin.getUsername(), election.getId()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Only a draft");
        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.CLOSED);
    }

    @Test
    @DisplayName("FR-05: eligibility is granted once per voter, only to voter accounts, and can be revoked")
    void eligibilityManagement() {
        Election election = adminService.createElection(admin.getUsername(), "Council", "", START, END);
        UserAccount voter = data.voter("voter");

        adminService.grantEligibility(admin.getUsername(), election.getId(), voter.getId());
        adminService.grantEligibility(admin.getUsername(), election.getId(), voter.getId());
        assertThat(eligibilityRepository.countByElectionIdAndActiveTrue(election.getId())).isEqualTo(1);
        assertThat(eventsOf(election, AuditEventType.ELIGIBILITY_GRANTED)).hasSize(1);

        assertThatThrownBy(() -> adminService.grantEligibility(admin.getUsername(), election.getId(), admin.getId()))
                .isInstanceOf(NotFoundException.class);

        adminService.revokeEligibility(admin.getUsername(), election.getId(), voter.getId());
        assertThat(eligibilityRepository.countByElectionIdAndActiveTrue(election.getId())).isZero();
        assertThatThrownBy(() -> adminService.revokeEligibility(admin.getUsername(), election.getId(), voter.getId()))
                .isInstanceOf(NotFoundException.class);

        adminService.grantEligibility(admin.getUsername(), election.getId(), voter.getId());
        assertThat(eligibilityRepository.existsByElectionIdAndVoterIdAndActiveTrue(election.getId(), voter.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("FR-05: eligibility cannot change after the election is closed or its deadline has passed")
    void eligibilityIsFrozenWhenVotingIsOver() {
        Election closed = activatedElection("Jordan Lee", "Priya Nair");
        Election ended = activatedElection("Jordan Lee", "Priya Nair");
        UserAccount voter = data.voter("voter");
        adminService.close(admin.getUsername(), closed.getId());

        assertThatThrownBy(() -> adminService.grantEligibility(admin.getUsername(), closed.getId(), voter.getId()))
                .isInstanceOf(BusinessRuleException.class);

        clock.setInstant(END);
        assertThatThrownBy(() -> adminService.grantEligibility(admin.getUsername(), ended.getId(), voter.getId()))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(eligibilityRepository.countByElectionIdAndActiveTrue(ended.getId())).isZero();
    }

    @Test
    @DisplayName("FR-14: nobody gets results while the election is a draft, scheduled or open")
    void resultsAreHiddenUntilVotingIsOver() {
        Election election = activatedElection("Jordan Lee", "Priya Nair");
        UserAccount voter = data.voter("voter");
        adminService.grantEligibility(admin.getUsername(), election.getId(), voter.getId());
        votingService.castVote(voter.getUsername(), election.getId(), data.candidatesOf(election).get(0).getId());

        assertThatThrownBy(() -> queryService.resultsFor(admin.getUsername(), election.getId()))
                .isInstanceOf(ResultsNotAvailableException.class);
        assertThatThrownBy(() -> queryService.resultsFor(voter.getUsername(), election.getId()))
                .isInstanceOf(ResultsNotAvailableException.class);

        clock.setInstant(START.minusSeconds(1));
        assertThatThrownBy(() -> queryService.resultsFor(admin.getUsername(), election.getId()))
                .isInstanceOf(ResultsNotAvailableException.class);

        Election draft = adminService.createElection(admin.getUsername(), "Draft", "", START, END);
        assertThatThrownBy(() -> queryService.resultsFor(admin.getUsername(), draft.getId()))
                .isInstanceOf(ResultsNotAvailableException.class);
    }

    @Test
    @DisplayName("FR-14: after the deadline the tally is published without an administrator closing the election")
    void resultsAppearAfterTheDeadline() {
        Election election = activatedElection("Jordan Lee", "Priya Nair", "Marcus Hill");
        List<Candidate> candidates = data.candidatesOf(election);
        UserAccount first = data.voter("first");
        UserAccount second = data.voter("second");
        UserAccount third = data.voter("third");
        for (UserAccount voter : List.of(first, second, third)) {
            adminService.grantEligibility(admin.getUsername(), election.getId(), voter.getId());
        }
        votingService.castVote(first.getUsername(), election.getId(), candidates.get(0).getId());
        votingService.castVote(second.getUsername(), election.getId(), candidates.get(0).getId());
        votingService.castVote(third.getUsername(), election.getId(), candidates.get(1).getId());

        clock.setInstant(END);
        ElectionResults results = queryService.resultsFor(first.getUsername(), election.getId());

        assertThat(results.phase()).isEqualTo(ElectionPhase.ENDED);
        assertThat(reload(election).getStatus()).isEqualTo(ElectionStatus.OPEN);
        assertThat(results.tally().totalVotes()).isEqualTo(3);
        assertThat(results.tally().rows()).extracting(CandidateTally::name)
                .containsExactly("Jordan Lee", "Priya Nair", "Marcus Hill");
        assertThat(results.tally().rows()).extracting(CandidateTally::votes).containsExactly(2L, 1L, 0L);
        assertThat(results.tally().outcome()).isEqualTo(TallyOutcome.WINNER);
        assertThat(results.tally().leaders()).extracting(CandidateTally::name).containsExactly("Jordan Lee");
        assertThat(results.tally().totalVotes()).isEqualTo(ballotRepository.countByElectionId(election.getId()));
    }

    @Test
    @DisplayName("FR-14: a closed election reports a tie as a tie, and an election without votes has no winner")
    void tieAndEmptyResults() {
        Election tied = activatedElection("Jordan Lee", "Priya Nair");
        List<Candidate> candidates = data.candidatesOf(tied);
        UserAccount first = data.voter("first");
        UserAccount second = data.voter("second");
        adminService.grantEligibility(admin.getUsername(), tied.getId(), first.getId());
        adminService.grantEligibility(admin.getUsername(), tied.getId(), second.getId());
        votingService.castVote(first.getUsername(), tied.getId(), candidates.get(0).getId());
        votingService.castVote(second.getUsername(), tied.getId(), candidates.get(1).getId());
        adminService.close(admin.getUsername(), tied.getId());

        ElectionResults tieResults = queryService.resultsFor(admin.getUsername(), tied.getId());
        assertThat(tieResults.phase()).isEqualTo(ElectionPhase.CLOSED);
        assertThat(tieResults.tally().outcome()).isEqualTo(TallyOutcome.TIE);
        assertThat(tieResults.tally().leaders()).hasSize(2);

        Election empty = activatedElection("Jordan Lee", "Priya Nair");
        adminService.close(admin.getUsername(), empty.getId());
        ElectionResults emptyResults = queryService.resultsFor(admin.getUsername(), empty.getId());
        assertThat(emptyResults.tally().outcome()).isEqualTo(TallyOutcome.NO_VOTES);
        assertThat(emptyResults.tally().leaders()).isEmpty();
        assertThat(emptyResults.tally().rows()).hasSize(2);
    }

    @Test
    @DisplayName("FR-14: a voter who was never eligible cannot view the results of an election")
    void resultsAreLimitedToRelatedVoters() {
        Election election = activatedElection("Jordan Lee", "Priya Nair");
        UserAccount stranger = data.voter("stranger");
        adminService.close(admin.getUsername(), election.getId());

        assertThatThrownBy(() -> queryService.resultsFor(stranger.getUsername(), election.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> queryService.resultsFor(admin.getUsername(), 9_999_999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("FR-08: the voter home page lists activated elections with the voter's participation status")
    void voterDashboard() {
        Election open = activatedElection("Jordan Lee", "Priya Nair");
        Election draft = adminService.createElection(admin.getUsername(), "Hidden draft", "", START, END);
        Election notEligible = activatedElection("Yes", "No");
        UserAccount voter = data.voter("voter");
        adminService.grantEligibility(admin.getUsername(), open.getId(), voter.getId());
        adminService.grantEligibility(admin.getUsername(), draft.getId(), voter.getId());

        List<VoterElectionRow> before = queryService.voterDashboard(voter.getUsername());
        assertThat(before).extracting(row -> row.election().getId()).containsExactly(open.getId());
        assertThat(before.get(0).canVote()).isTrue();
        assertThat(before.get(0).hasVoted()).isFalse();
        assertThat(before.get(0).phase()).isEqualTo(ElectionPhase.VOTING);
        assertThat(before.get(0).resultsVisible()).isFalse();
        assertThat(before).extracting(row -> row.election().getId()).doesNotContain(notEligible.getId());

        votingService.castVote(voter.getUsername(), open.getId(), data.candidatesOf(open).get(0).getId());
        adminService.revokeEligibility(admin.getUsername(), open.getId(), voter.getId());

        List<VoterElectionRow> after = queryService.voterDashboard(voter.getUsername());
        assertThat(after).singleElement().satisfies(row -> {
            assertThat(row.hasVoted()).isTrue();
            assertThat(row.canVote()).isFalse();
            assertThat(row.eligible()).isFalse();
        });
        assertThat(queryService.hasVoted(voter.getUsername(), open.getId())).isTrue();
        assertThat(queryService.hasVoted(voter.getUsername(), notEligible.getId())).isFalse();
    }

    @Test
    @DisplayName("FR-15: the overview counts participation, ballots and rejected attempts per election")
    void overviewCounts() {
        Election election = activatedElection("Jordan Lee", "Priya Nair");
        UserAccount voter = data.voter("voter");
        UserAccount outsider = data.voter("outsider");
        adminService.grantEligibility(admin.getUsername(), election.getId(), voter.getId());
        Long candidateId = data.candidatesOf(election).get(0).getId();
        votingService.castVote(voter.getUsername(), election.getId(), candidateId);
        assertThatThrownBy(() -> votingService.castVote(voter.getUsername(), election.getId(), candidateId))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> votingService.castVote(outsider.getUsername(), election.getId(), candidateId))
                .isInstanceOf(RuntimeException.class);

        ElectionAdminDetail detail = queryService.adminDetail(election.getId());
        ElectionOverview overview = detail.overview();

        assertThat(overview.candidateCount()).isEqualTo(2);
        assertThat(overview.eligibleCount()).isEqualTo(1);
        assertThat(overview.participationCount()).isEqualTo(1);
        assertThat(overview.ballotCount()).isEqualTo(1);
        assertThat(overview.rejectedAttempts()).isEqualTo(2);
        assertThat(overview.countsMatch()).isTrue();
        assertThat(overview.resultsVisible()).isFalse();
        assertThat(detail.setupEditable()).isFalse();
        assertThat(detail.closable()).isTrue();
        assertThat(detail.eligibilityManageable()).isTrue();
        assertThat(detail.voters()).filteredOn(row -> row.voter().getId().equals(voter.getId())).singleElement()
                .satisfies(row -> {
                    assertThat(row.eligible()).isTrue();
                    assertThat(row.hasVoted()).isTrue();
                });
        assertThat(queryService.listOverviews()).extracting(row -> row.election().getId()).contains(election.getId());
    }

    @Test
    @DisplayName("FR-02/NFR-04: a created voter gets a bcrypt password hash, and invalid or duplicate data is refused")
    void voterAccountCreation() {
        UserAccount created = userAdminService.createVoter(admin.getUsername(), "new.voter-1", "New Voter",
                "Correct-Horse-9");

        UserAccount stored = userAccountRepository.findByUsername("new.voter-1").orElseThrow();
        assertThat(stored.getId()).isEqualTo(created.getId());
        assertThat(stored.isEnabled()).isTrue();
        assertThat(stored.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("Correct-Horse-9");
        assertThat(passwordEncoder.matches("Correct-Horse-9", stored.getPasswordHash())).isTrue();
        assertThat(userAdminService.listVoters()).extracting(UserAccount::getUsername).contains("new.voter-1");

        assertThatThrownBy(() -> userAdminService.createVoter(admin.getUsername(), "new.voter-1", "Twin",
                "Correct-Horse-9")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("already taken");
        assertThatThrownBy(() -> userAdminService.createVoter(admin.getUsername(), "Bad Name!", "X", "Correct-Horse-9"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userAdminService.createVoter(admin.getUsername(), "short.pw", "X", "1234567"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userAdminService.createVoter(admin.getUsername(), "no.name", "  ", "Correct-Horse-9"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(userAccountRepository.existsByUsername("short.pw")).isFalse();
    }

    @Test
    @DisplayName("FR-02: voter accounts are disabled and re-enabled, never deleted; administrators are not affected")
    void enableAndDisableVoter() {
        UserAccount voter = data.voter("voter");

        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), false);
        assertThat(userAccountRepository.findById(voter.getId()).orElseThrow().isEnabled()).isFalse();

        userAdminService.setVoterEnabled(admin.getUsername(), voter.getId(), true);
        assertThat(userAccountRepository.findById(voter.getId()).orElseThrow().isEnabled()).isTrue();

        assertThatThrownBy(() -> userAdminService.setVoterEnabled(admin.getUsername(), admin.getId(), false))
                .isInstanceOf(NotFoundException.class);
        assertThat(userAccountRepository.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(participationRepository.findByVoterId(voter.getId())).isEmpty();
    }
}
