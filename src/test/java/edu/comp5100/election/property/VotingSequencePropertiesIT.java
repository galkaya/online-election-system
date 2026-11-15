package edu.comp5100.election.property;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.ElectionApplication;
import edu.comp5100.election.domain.Candidate;
import edu.comp5100.election.domain.Election;
import edu.comp5100.election.domain.UserAccount;
import edu.comp5100.election.policy.CandidateOption;
import edu.comp5100.election.policy.TallyCalculator;
import edu.comp5100.election.policy.TallyResult;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.repository.ParticipationRepository;
import edu.comp5100.election.service.VoteRejectedException;
import edu.comp5100.election.service.VotingService;
import edu.comp5100.election.support.TestData;
import edu.comp5100.election.support.TestSupportConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Database-backed property test of the one-vote rule (FR-10, FR-11, FR-14), run by the jqwik
 * engine against the real services and an isolated H2 database. jqwik has no Spring test
 * integration here, so the class starts the application context itself, once. Each try uses a
 * fresh election and fresh voters; the number of tries is kept small because every try performs
 * real database transactions.
 */
@Label("Voting sequence properties with a real database (jqwik)")
class VotingSequencePropertiesIT {

    private static final Instant BASE = TestSupportConfig.BASE_TIME;
    private static final int ELIGIBLE_VOTERS = 4;
    private static final int ALL_VOTERS = 6;
    private static final int VALID_CANDIDATES = 3;

    private static ConfigurableApplicationContext context;

    /** One vote attempt: who tries, and for whom. Candidate index 3 means "a candidate of another election". */
    record Attempt(int voterIndex, int candidateIndex) {
    }

    @BeforeContainer
    static void startApplication() {
        context = new SpringApplicationBuilder(ElectionApplication.class, TestSupportConfig.class)
                .profiles("test")
                .properties("server.port=0")
                .run();
    }

    @AfterContainer
    static void stopApplication() {
        if (context != null) {
            context.close();
        }
    }

    @Provide
    Arbitrary<List<Attempt>> attemptSequences() {
        Arbitrary<Integer> voters = Arbitraries.integers().between(0, ALL_VOTERS - 1);
        Arbitrary<Integer> candidateChoices = Arbitraries.integers().between(0, VALID_CANDIDATES);
        return Combinators.combine(voters, candidateChoices).as(Attempt::new).list().ofMinSize(1).ofMaxSize(15);
    }

    @Property(tries = 25)
    @Label("Each attempt either adds exactly one vote for the chosen candidate or changes nothing at all")
    void everyAttemptAddsOneVoteOrNothing(@ForAll("attemptSequences") List<Attempt> attempts) {
        TestData data = context.getBean(TestData.class);
        VotingService votingService = context.getBean(VotingService.class);
        BallotRepository ballotRepository = context.getBean(BallotRepository.class);
        ParticipationRepository participationRepository = context.getBean(ParticipationRepository.class);

        Election election = data.openElection("Property election", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Jordan Lee", "Priya Nair", "Marcus Hill");
        Election otherElection = data.openElection("Property other", BASE.minus(Duration.ofHours(1)),
                BASE.plus(Duration.ofHours(1)), "Foreign Candidate", "Second Foreign Candidate");
        List<Candidate> candidates = data.candidatesOf(election);
        List<Long> candidateChoices = new ArrayList<>(candidates.stream().map(Candidate::getId).toList());
        candidateChoices.add(data.candidatesOf(otherElection).get(0).getId());
        List<CandidateOption> options = candidates.stream()
                .map(candidate -> new CandidateOption(candidate.getId(), candidate.getName()))
                .toList();
        List<UserAccount> voters = new ArrayList<>();
        for (int index = 0; index < ALL_VOTERS; index++) {
            UserAccount voter = data.voter("prop-voter");
            voters.add(voter);
            if (index < ELIGIBLE_VOTERS) {
                data.makeEligible(election, voter);
            }
        }

        Set<Integer> votersWhoVoted = new HashSet<>();
        for (Attempt attempt : attempts) {
            Long candidateId = candidateChoices.get(attempt.candidateIndex());
            TallyResult before = TallyCalculator.tally(options,
                    ballotRepository.findCandidateIdsByElectionId(election.getId()));

            boolean accepted;
            try {
                votingService.castVote(voters.get(attempt.voterIndex()).getUsername(), election.getId(), candidateId);
                accepted = true;
            } catch (VoteRejectedException rejected) {
                accepted = false;
            }

            // The requirement, not the implementation: eligible voter, own candidate, first vote.
            boolean mustBeAccepted = attempt.voterIndex() < ELIGIBLE_VOTERS
                    && attempt.candidateIndex() < VALID_CANDIDATES
                    && !votersWhoVoted.contains(attempt.voterIndex());
            assertThat(accepted).as("attempt %s", attempt).isEqualTo(mustBeAccepted);

            TallyResult after = TallyCalculator.tally(options,
                    ballotRepository.findCandidateIdsByElectionId(election.getId()));
            if (accepted) {
                votersWhoVoted.add(attempt.voterIndex());
                assertThat(after.totalVotes()).isEqualTo(before.totalVotes() + 1);
                for (int index = 0; index < options.size(); index++) {
                    long expected = before.rows().get(index).votes()
                            + (options.get(index).id() == candidateId ? 1 : 0);
                    assertThat(after.rows().get(index).votes()).isEqualTo(expected);
                }
            } else {
                assertThat(after).as("a rejected attempt must not change the tally").isEqualTo(before);
            }
        }

        assertThat(ballotRepository.countByElectionId(election.getId())).isEqualTo(votersWhoVoted.size());
        assertThat(participationRepository.countByElectionId(election.getId())).isEqualTo(votersWhoVoted.size());
        assertThat(ballotRepository.countByElectionId(otherElection.getId())).isZero();
    }
}
