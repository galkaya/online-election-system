package edu.comp5100.election.property;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.policy.CandidateOption;
import edu.comp5100.election.policy.CandidateTally;
import edu.comp5100.election.policy.TallyCalculator;
import edu.comp5100.election.policy.TallyOutcome;
import edu.comp5100.election.policy.TallyResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * Property-based and metamorphic tests of the production tally (FR-14), run by the jqwik engine
 * on the JUnit Platform. The expectations are stated as invariants and relations between two
 * runs; the tests do not contain a second counting algorithm to compare against.
 */
@Label("Tally properties (jqwik)")
class TallyProperties {

    /** An election with 1 to 8 candidates and 0 to 200 ballots, each naming one of those candidates. */
    record TallyInput(List<CandidateOption> candidates, List<Long> ballots) {
    }

    @Provide
    Arbitrary<TallyInput> elections() {
        return Arbitraries.integers().between(1, 8).flatMap(candidateCount -> {
            List<CandidateOption> candidates = IntStream.range(0, candidateCount)
                    .mapToObj(index -> new CandidateOption(100L + index, "Candidate " + index))
                    .toList();
            return Arbitraries.integers().between(0, candidateCount - 1).list().ofMaxSize(200)
                    .map(indices -> new TallyInput(candidates,
                            indices.stream().map(index -> candidates.get(index).id()).toList()));
        });
    }

    @Property
    @Label("The total equals the number of ballots and the sum of all candidate totals")
    void totalEqualsBallotCount(@ForAll("elections") TallyInput input) {
        TallyResult result = TallyCalculator.tally(input.candidates(), input.ballots());

        assertThat(result.totalVotes()).isEqualTo(input.ballots().size());
        assertThat(result.rows().stream().mapToLong(CandidateTally::votes).sum()).isEqualTo(input.ballots().size());
    }

    @Property
    @Label("Every candidate is listed exactly once, in ballot order, and no total is negative")
    void everyCandidateIsListedWithANonNegativeTotal(@ForAll("elections") TallyInput input) {
        TallyResult result = TallyCalculator.tally(input.candidates(), input.ballots());

        assertThat(result.rows()).extracting(CandidateTally::candidateId)
                .containsExactlyElementsOf(input.candidates().stream().map(CandidateOption::id).toList());
        assertThat(result.rows()).allMatch(row -> row.votes() >= 0);
        assertThat(result.rows()).allMatch(row -> row.percent() >= 0.0 && row.percent() <= 100.0);
    }

    @Property
    @Label("Metamorphic: shuffling the ballots does not change the result")
    void ballotOrderDoesNotMatter(@ForAll("elections") TallyInput input, @ForAll long shuffleSeed) {
        List<Long> shuffled = new ArrayList<>(input.ballots());
        Collections.shuffle(shuffled, new Random(shuffleSeed));

        assertThat(TallyCalculator.tally(input.candidates(), shuffled))
                .isEqualTo(TallyCalculator.tally(input.candidates(), input.ballots()));
    }

    @Property
    @Label("Metamorphic: one more ballot raises exactly that candidate and the total by one")
    void oneMoreBallotAddsExactlyOneVote(@ForAll("elections") TallyInput input, @ForAll @IntRange(max = 7) int pick) {
        CandidateOption chosen = input.candidates().get(pick % input.candidates().size());
        List<Long> extended = new ArrayList<>(input.ballots());
        extended.add(chosen.id());

        TallyResult before = TallyCalculator.tally(input.candidates(), input.ballots());
        TallyResult after = TallyCalculator.tally(input.candidates(), extended);

        assertThat(after.totalVotes()).isEqualTo(before.totalVotes() + 1);
        for (int index = 0; index < before.rows().size(); index++) {
            CandidateTally rowBefore = before.rows().get(index);
            CandidateTally rowAfter = after.rows().get(index);
            long expected = rowBefore.candidateId() == chosen.id() ? rowBefore.votes() + 1 : rowBefore.votes();
            assertThat(rowAfter.votes()).isEqualTo(expected);
        }
    }

    @Property
    @Label("The outcome agrees with the totals: no votes, one leader, or a tie of all leaders")
    void outcomeAgreesWithTotals(@ForAll("elections") TallyInput input) {
        TallyResult result = TallyCalculator.tally(input.candidates(), input.ballots());
        long highest = result.rows().stream().mapToLong(CandidateTally::votes).max().orElseThrow();

        if (input.ballots().isEmpty()) {
            assertThat(result.outcome()).isEqualTo(TallyOutcome.NO_VOTES);
            assertThat(result.leaders()).isEmpty();
            return;
        }
        assertThat(result.leaders()).isNotEmpty().allMatch(leader -> leader.votes() == highest);
        assertThat(result.rows()).filteredOn(row -> row.votes() == highest)
                .containsExactlyElementsOf(result.leaders());
        assertThat(result.outcome()).isEqualTo(result.leaders().size() == 1 ? TallyOutcome.WINNER : TallyOutcome.TIE);
    }
}
