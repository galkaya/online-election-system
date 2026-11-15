package edu.comp5100.election.support;

import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.ElectionEligibility;
import edu.comp5100.election.domain.Role;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates fictional test data directly through the repositories. Usernames are unique per call, so
 * test classes that share one Spring context and database never collide. Passwords use the
 * "{noop}" encoding to keep the suites fast; the real bcrypt path is covered by the tests of
 * UserAdminService and by the demo seed.
 */
public class TestData {

    public static final String PASSWORD = "Test-Password-1";

    private final AtomicInteger sequence = new AtomicInteger();
    private final UserAccountRepository userAccountRepository;
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final ElectionEligibilityRepository eligibilityRepository;

    public TestData(UserAccountRepository userAccountRepository, ElectionRepository electionRepository,
            CandidateRepository candidateRepository, ElectionEligibilityRepository eligibilityRepository) {
        this.userAccountRepository = userAccountRepository;
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.eligibilityRepository = eligibilityRepository;
    }

    public UserAccount voter(String prefix) {
        return account(prefix, Role.VOTER);
    }

    public UserAccount admin(String prefix) {
        return account(prefix, Role.ADMIN);
    }

    private UserAccount account(String prefix, Role role) {
        String username = prefix + "-" + sequence.incrementAndGet();
        return userAccountRepository.save(new UserAccount(username, "{noop}" + PASSWORD, "Test " + username, role,
                TestSupportConfig.BASE_TIME));
    }

    public Election draftElection(String title, Instant startsAt, Instant endsAt, String... candidateNames) {
        Election election = electionRepository.save(new Election(title + " #" + sequence.incrementAndGet(),
                "Test election", startsAt, endsAt, TestSupportConfig.BASE_TIME));
        for (String candidateName : candidateNames) {
            candidate(election, candidateName);
        }
        return election;
    }

    public Election openElection(String title, Instant startsAt, Instant endsAt, String... candidateNames) {
        Election election = draftElection(title, startsAt, endsAt, candidateNames);
        election.markOpen();
        return electionRepository.save(election);
    }

    public Candidate candidate(Election election, String name) {
        return candidateRepository.save(new Candidate(election.getId(), name, "Statement of " + name));
    }

    public List<Candidate> candidatesOf(Election election) {
        return candidateRepository.findByElectionIdOrderByIdAsc(election.getId());
    }

    public void makeEligible(Election election, UserAccount... voters) {
        for (UserAccount voter : voters) {
            eligibilityRepository.save(new ElectionEligibility(election.getId(), voter.getId()));
        }
    }
}
