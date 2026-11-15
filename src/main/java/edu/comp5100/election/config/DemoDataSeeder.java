package edu.comp5100.election.config;

import edu.comp5100.election.domain.Ballot;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionEligibility;
import edu.comp5100.election.domain.Participation;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import edu.comp5100.election.service.ReceiptGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-18: fictional demo data, created only in the "demo" profile and only when the database has
 * no accounts yet. Dates are relative to the moment of seeding and are never moved afterwards.
 * The accounts below are fictional and exist only in the local demo database.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "Admin-Demo-2026";
    public static final String VOTER_PASSWORD = "Voter-Demo-2026";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserAccountRepository userAccountRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionEligibilityRepository eligibilityRepository;
    private final ParticipationRepository participationRepository;
    private final BallotRepository ballotRepository;
    private final ReceiptGenerator receiptGenerator;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DemoDataSeeder(UserAccountRepository userAccountRepository, ElectionRepository electionRepository,
            CandidateRepository candidateRepository, ElectionEligibilityRepository eligibilityRepository,
            ParticipationRepository participationRepository, BallotRepository ballotRepository,
            ReceiptGenerator receiptGenerator, PasswordEncoder passwordEncoder, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.participationRepository = participationRepository;
        this.ballotRepository = ballotRepository;
        this.receiptGenerator = receiptGenerator;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userAccountRepository.count() > 0) {
            log.info("Demo data already present; nothing was changed.");
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);

        userAccountRepository.save(new UserAccount(ADMIN_USERNAME, passwordEncoder.encode(ADMIN_PASSWORD),
                "Morgan Reyes (Election Administrator)", Role.ADMIN, now));
        UserAccount voter1 = voter("voter1", "Ava Brooks", now);
        UserAccount voter2 = voter("voter2", "Ben Carter", now);
        UserAccount voter3 = voter("voter3", "Chloe Diaz", now);
        voter("voter4", "Dylan Evans", now); // exists, but is eligible for no election
        UserAccount voter5 = voter("voter5", "Ella Foster", now);
        voter5.setEnabled(false); // eligible on paper, but the account is disabled

        // 1. Main demo election: a ready-to-activate draft with three candidates.
        Election council = electionRepository.save(new Election("Student Council President 2026",
                "Choose the next president of the student council. This is a classroom simulation with "
                        + "fictional candidates and voters.",
                now, now.plus(Duration.ofDays(14)), now));
        candidate(council, "Jordan Lee", "Third-year computer science student. Wants longer library hours and "
                + "more study rooms.");
        candidate(council, "Priya Nair", "Second-year biology student. Wants cheaper campus meals and a "
                + "student food pantry.");
        candidate(council, "Marcus Hill", "Fourth-year business student. Wants more funding for clubs and "
                + "campus events.");
        for (UserAccount eligibleVoter : List.of(voter1, voter2, voter3, voter5)) {
            eligibilityRepository.save(new ElectionEligibility(council.getId(), eligibleVoter.getId()));
        }

        // 2. Deadline fixture: activated, never closed by an administrator, deadline already passed.
        Election referendum = electionRepository.save(new Election("Library Hours Referendum",
                "Should the main library stay open until midnight on weekdays? The voting period of this "
                        + "election has already ended.",
                now.minus(Duration.ofDays(3)), now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(4))));
        Candidate yes = candidate(referendum, "Yes, extend the hours", "Keep the library open until midnight.");
        Candidate no = candidate(referendum, "No, keep the current hours", "Keep closing the library at 9 PM.");
        for (UserAccount eligibleVoter : List.of(voter1, voter2, voter3)) {
            eligibilityRepository.save(new ElectionEligibility(referendum.getId(), eligibleVoter.getId()));
        }
        referendum.markOpen();
        // Two fictional votes cast before the deadline; voter3 did not vote and can no longer do so.
        seedVote(referendum, voter1, yes);
        seedVote(referendum, voter2, no);

        // 3. Upcoming fixture: activated, but the voting window has not started.
        Election festival = electionRepository.save(new Election("Spring Festival Theme Vote",
                "Pick the theme of next year's spring festival. Voting has not started yet.",
                now.plus(Duration.ofDays(7)), now.plus(Duration.ofDays(14)), now));
        candidate(festival, "Around the World", "Food, music and games from many countries.");
        candidate(festival, "Retro Arcade", "Classic video games and neon lights.");
        for (UserAccount eligibleVoter : List.of(voter1, voter2, voter3)) {
            eligibilityRepository.save(new ElectionEligibility(festival.getId(), eligibleVoter.getId()));
        }
        festival.markOpen();

        log.info("Demo data created: 1 administrator, 5 voters, 3 elections.");
    }

    private UserAccount voter(String username, String displayName, Instant now) {
        return userAccountRepository.save(new UserAccount(username, passwordEncoder.encode(VOTER_PASSWORD),
                displayName, Role.VOTER, now));
    }

    private Candidate candidate(Election election, String name, String biography) {
        return candidateRepository.save(new Candidate(election.getId(), name, biography));
    }

    /** The confirmation codes of these seeded fixture votes are discarded; nobody needs to verify them. */
    private void seedVote(Election election, UserAccount voter, Candidate candidate) {
        participationRepository.save(new Participation(election.getId(), voter.getId()));
        ballotRepository.save(new Ballot(election.getId(), candidate.getId(),
                ReceiptCodec.hash(receiptGenerator.newCode())));
    }
}
