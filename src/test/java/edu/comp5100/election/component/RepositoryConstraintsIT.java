package edu.comp5100.election.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.domain.Ballot;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionEligibility;
import edu.comp5100.election.domain.Participation;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Repository component tests: real Spring Data JPA repositories, Hibernate, the Flyway schema and
 * a real embedded H2 database. No services, no web layer. Each test runs in a transaction that is
 * rolled back afterwards.
 */
@DataJpaTest
@DisplayName("Repository and schema constraints (JPA + H2)")
class RepositoryConstraintsIT {

    private static final Instant START = Instant.parse("2026-11-19T15:00:00Z");
    private static final Instant END = Instant.parse("2026-11-20T15:00:00Z");
    private static final String HASH_A = "a".repeat(64);
    private static final String HASH_B = "b".repeat(64);

    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private ElectionRepository electionRepository;
    @Autowired
    private CandidateRepository candidateRepository;
    @Autowired
    private ElectionEligibilityRepository eligibilityRepository;
    @Autowired
    private ParticipationRepository participationRepository;
    @Autowired
    private BallotRepository ballotRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserAccount voter;
    private Election election;
    private Election otherElection;
    private Candidate candidate;
    private Candidate otherCandidate;

    @BeforeEach
    void createRows() {
        voter = userAccountRepository.saveAndFlush(new UserAccount("repo-voter", "{noop}x", "Repo Voter", Role.VOTER,
                START));
        election = electionRepository.saveAndFlush(new Election("Election A", "First", START, END, START));
        otherElection = electionRepository.saveAndFlush(new Election("Election B", "Second", START, END, START));
        candidate = candidateRepository.saveAndFlush(new Candidate(election.getId(), "Jordan Lee", "A"));
        otherCandidate = candidateRepository.saveAndFlush(new Candidate(otherElection.getId(), "Priya Nair", "B"));
    }

    @Test
    @DisplayName("NFR-02: the database refuses a second participation for the same voter and election")
    void participationIsUniquePerVoterAndElection() {
        participationRepository.saveAndFlush(new Participation(election.getId(), voter.getId()));

        assertThatThrownBy(() -> participationRepository.saveAndFlush(
                new Participation(election.getId(), voter.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR-10: the same voter may participate in a different election")
    void participationInAnotherElectionIsAllowed() {
        participationRepository.saveAndFlush(new Participation(election.getId(), voter.getId()));
        participationRepository.saveAndFlush(new Participation(otherElection.getId(), voter.getId()));

        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(1);
        assertThat(participationRepository.countByElectionId(otherElection.getId())).isEqualTo(1);
        assertThat(participationRepository.existsByElectionIdAndVoterId(election.getId(), voter.getId())).isTrue();
    }

    @Test
    @DisplayName("FR-11: the database refuses a ballot whose candidate belongs to another election")
    void ballotCandidateMustBelongToTheBallotElection() {
        assertThatThrownBy(() -> ballotRepository.saveAndFlush(
                new Ballot(election.getId(), otherCandidate.getId(), HASH_A)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR-12: two ballots cannot share a receipt hash")
    void receiptHashIsUnique() {
        ballotRepository.saveAndFlush(new Ballot(election.getId(), candidate.getId(), HASH_A));

        assertThatThrownBy(() -> ballotRepository.saveAndFlush(
                new Ballot(election.getId(), candidate.getId(), HASH_A)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NFR-01: ballot keys are random version-4 UUIDs, not a sequence")
    void ballotIdsAreRandomUuids() {
        Ballot first = ballotRepository.saveAndFlush(new Ballot(election.getId(), candidate.getId(), HASH_A));
        Ballot second = ballotRepository.saveAndFlush(new Ballot(election.getId(), candidate.getId(), HASH_B));

        assertThat(first.getId()).isNotNull().isNotEqualTo(second.getId());
        assertThat(first.getId().version()).isEqualTo(4);
        assertThat(second.getId().version()).isEqualTo(4);
    }

    @Test
    @DisplayName("FR-14: the tally query returns the candidate ids of one election only")
    void candidateIdsAreReadPerElection() {
        ballotRepository.saveAndFlush(new Ballot(election.getId(), candidate.getId(), HASH_A));
        ballotRepository.saveAndFlush(new Ballot(otherElection.getId(), otherCandidate.getId(), HASH_B));

        assertThat(ballotRepository.findCandidateIdsByElectionId(election.getId())).containsExactly(candidate.getId());
        assertThat(ballotRepository.countByElectionId(otherElection.getId())).isEqualTo(1);
        assertThat(ballotRepository.existsByReceiptHash(HASH_A)).isTrue();
        assertThat(ballotRepository.existsByReceiptHash("c".repeat(64))).isFalse();
    }

    @Test
    @DisplayName("FR-04: candidate names are unique inside an election but may repeat across elections")
    void candidateNamesAreUniquePerElection() {
        candidateRepository.saveAndFlush(new Candidate(otherElection.getId(), "Jordan Lee", "Same name elsewhere"));

        assertThatThrownBy(() -> candidateRepository.saveAndFlush(
                new Candidate(election.getId(), "Jordan Lee", "Duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR-04: candidates are found only through their own election")
    void candidateLookupIsScopedToTheElection() {
        assertThat(candidateRepository.existsByIdAndElectionId(candidate.getId(), election.getId())).isTrue();
        assertThat(candidateRepository.existsByIdAndElectionId(candidate.getId(), otherElection.getId())).isFalse();
        assertThat(candidateRepository.findByElectionIdOrderByIdAsc(election.getId()))
                .extracting(Candidate::getName).containsExactly("Jordan Lee");
    }

    @Test
    @DisplayName("FR-03: the database refuses an election whose start is not before its end")
    void electionWindowIsCheckedByTheDatabase() {
        assertThatThrownBy(() -> electionRepository.saveAndFlush(new Election("Bad", "Bad window", END, START, START)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR-05: there is at most one eligibility row per voter and election")
    void eligibilityIsUniquePerVoterAndElection() {
        eligibilityRepository.saveAndFlush(new ElectionEligibility(election.getId(), voter.getId()));

        assertThatThrownBy(() -> eligibilityRepository.saveAndFlush(
                new ElectionEligibility(election.getId(), voter.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FR-01: usernames are unique")
    void usernamesAreUnique() {
        assertThatThrownBy(() -> userAccountRepository.saveAndFlush(
                new UserAccount("repo-voter", "{noop}y", "Twin", Role.VOTER, START)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NFR-02: the database accepts only the known role and status values")
    void roleAndStatusValuesAreCheckedByTheDatabase() {
        // The valid rows of createRows() were inserted through a connection other than the one that
        // created the schema. This is the exact situation in which an IN-list check failed on H2 2.4.240.
        assertThat(userAccountRepository.count()).isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into app_user (username, password_hash, display_name, role, enabled, created_at) "
                        + "values ('intruder', 'x', 'Intruder', 'SUPERUSER', true, current_timestamp)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update election set status = 'REOPENED' where id = ?", election.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("NFR-03: the election row can be loaded with a pessimistic lock")
    void electionCanBeLockedForUpdate() {
        assertThat(electionRepository.findByIdForUpdate(election.getId())).isPresent();
        assertThat(electionRepository.findByIdForUpdate(-1L)).isEmpty();
    }

    @Test
    @DisplayName("NFR-01: the ballot table has no column that could identify a voter or a time")
    void ballotTableHasNoVoterColumns() {
        assertThat(columnsOf("BALLOT")).containsExactlyInAnyOrder("ID", "ELECTION_ID", "CANDIDATE_ID", "RECEIPT_HASH");
    }

    @Test
    @DisplayName("NFR-01: the participation and eligibility tables hold no candidate, ballot, receipt or time")
    void participationTablesHaveNoChoiceColumns() {
        assertThat(columnsOf("PARTICIPATION")).containsExactlyInAnyOrder("ID", "ELECTION_ID", "VOTER_ID");
        assertThat(columnsOf("ELECTION_ELIGIBILITY"))
                .containsExactlyInAnyOrder("ID", "ELECTION_ID", "VOTER_ID", "ACTIVE");
    }

    @Test
    @DisplayName("NFR-01: the audit table has no candidate, ballot or receipt column")
    void auditTableHasNoChoiceColumns() {
        assertThat(columnsOf("AUDIT_EVENT"))
                .containsExactlyInAnyOrder("ID", "OCCURRED_AT", "EVENT_TYPE", "ACTOR", "ELECTION_ID", "DETAIL");
    }

    private List<String> columnsOf(String table) {
        return jdbcTemplate.queryForList(
                "select column_name from information_schema.columns where table_schema = 'PUBLIC' and table_name = ?",
                String.class, table);
    }
}
