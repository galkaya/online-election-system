package edu.comp5100.election.integration;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.config.DemoDataSeeder;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionStatus;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/**
 * FR-18: the demo seed, run against an isolated in-memory database (the "test" profile overrides
 * the demo file database). The real system clock is used here, as in the real demo.
 */
@SpringBootTest
@ActiveProfiles({"demo", "test"})
@DisplayName("Demo data seeding")
class DemoSeedIT {

    @Autowired
    private DemoDataSeeder seeder;
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
    private PasswordEncoder passwordEncoder;

    private Election election(String title) {
        return electionRepository.findAll().stream()
                .filter(candidate -> candidate.getTitle().equals(title))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("FR-18: the seed creates one administrator, five voters and the three demo elections")
    void seedCreatesTheDemoFixtures() {
        assertThat(userAccountRepository.findByRoleOrderByUsernameAsc(Role.ADMIN))
                .extracting(UserAccount::getUsername).containsExactly("admin");
        assertThat(userAccountRepository.findByRoleOrderByUsernameAsc(Role.VOTER))
                .extracting(UserAccount::getUsername)
                .containsExactly("voter1", "voter2", "voter3", "voter4", "voter5");
        assertThat(userAccountRepository.findByUsername("voter5").orElseThrow().isEnabled()).isFalse();
        UserAccount admin = userAccountRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(DemoDataSeeder.ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();

        Election council = election("Student Council President 2026");
        assertThat(council.getStatus()).isEqualTo(ElectionStatus.DRAFT);
        assertThat(candidateRepository.countByElectionId(council.getId())).isEqualTo(3);
        assertThat(eligibilityRepository.countByElectionIdAndActiveTrue(council.getId())).isEqualTo(4);
        Long voter4 = userAccountRepository.findByUsername("voter4").orElseThrow().getId();
        assertThat(eligibilityRepository.findByVoterId(voter4)).isEmpty();
    }

    @Test
    @DisplayName("FR-18: the deadline fixture is open but expired, and the upcoming fixture has not started")
    void seedCreatesTimeFixturesRelativeToNow() {
        Instant now = Instant.now();
        Election referendum = election("Library Hours Referendum");
        assertThat(referendum.getStatus()).isEqualTo(ElectionStatus.OPEN);
        assertThat(referendum.getEndsAt()).isBefore(now);
        assertThat(participationRepository.countByElectionId(referendum.getId())).isEqualTo(2);
        assertThat(ballotRepository.countByElectionId(referendum.getId())).isEqualTo(2);

        Election festival = election("Spring Festival Theme Vote");
        assertThat(festival.getStatus()).isEqualTo(ElectionStatus.OPEN);
        assertThat(festival.getStartsAt()).isAfter(now);
    }

    @Test
    @DisplayName("FR-18: running the seed again changes nothing")
    void seedIsIdempotent() {
        long users = userAccountRepository.count();
        long elections = electionRepository.count();
        List<Instant> windowsBefore = electionRepository.findAll().stream().map(Election::getEndsAt).toList();

        seeder.run(new DefaultApplicationArguments());

        assertThat(userAccountRepository.count()).isEqualTo(users);
        assertThat(electionRepository.count()).isEqualTo(elections);
        assertThat(electionRepository.findAll().stream().map(Election::getEndsAt).toList()).isEqualTo(windowsBefore);
    }
}
