package edu.comp5100.election.support;

import edu.comp5100.election.repository.CandidateRepository;
import edu.comp5100.election.repository.ElectionEligibilityRepository;
import edu.comp5100.election.repository.ElectionRepository;
import edu.comp5100.election.repository.UserAccountRepository;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test-only beans: a controllable clock that replaces the system clock, and a test data helper. */
@TestConfiguration
public class TestSupportConfig {

    /** Thursday, Nov 19, 2026, 9:00 AM in America/Chicago. Tests reset the clock to this instant. */
    public static final Instant BASE_TIME = Instant.parse("2026-11-19T15:00:00Z");

    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock(BASE_TIME);
    }

    @Bean
    public TestData testData(UserAccountRepository userAccountRepository, ElectionRepository electionRepository,
            CandidateRepository candidateRepository, ElectionEligibilityRepository eligibilityRepository) {
        return new TestData(userAccountRepository, electionRepository, candidateRepository, eligibilityRepository);
    }
}
