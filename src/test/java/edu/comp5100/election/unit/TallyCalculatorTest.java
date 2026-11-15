package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.policy.CandidateOption;
import edu.comp5100.election.policy.CandidateTally;
import edu.comp5100.election.policy.TallyCalculator;
import edu.comp5100.election.policy.TallyOutcome;
import edu.comp5100.election.policy.TallyResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Unit tests for counting ballots (FR-14). */
@DisplayName("Tally calculator")
class TallyCalculatorTest {

    private static final CandidateOption JORDAN = new CandidateOption(11, "Jordan Lee");
    private static final CandidateOption PRIYA = new CandidateOption(12, "Priya Nair");
    private static final CandidateOption MARCUS = new CandidateOption(13, "Marcus Hill");
    private static final List<CandidateOption> CANDIDATES = List.of(JORDAN, PRIYA, MARCUS);

    @Test
    @DisplayName("FR-14: counts every ballot, lists zero-vote candidates and names the single winner")
    void countsVotesAndFindsWinner() {
        TallyResult result = TallyCalculator.tally(CANDIDATES, List.of(11L, 12L, 11L));

        assertThat(result.totalVotes()).isEqualTo(3);
        assertThat(result.rows()).extracting(CandidateTally::name)
                .containsExactly("Jordan Lee", "Priya Nair", "Marcus Hill");
        assertThat(result.rows()).extracting(CandidateTally::votes).containsExactly(2L, 1L, 0L);
        assertThat(result.rows()).extracting(CandidateTally::percent).containsExactly(66.7, 33.3, 0.0);
        assertThat(result.outcome()).isEqualTo(TallyOutcome.WINNER);
        assertThat(result.leaders()).extracting(CandidateTally::candidateId).containsExactly(11L);
    }

    @Test
    @DisplayName("FR-14: a tie is reported as a tie with every leader, never as an arbitrary winner")
    void tieIsReportedAsTie() {
        TallyResult result = TallyCalculator.tally(CANDIDATES, List.of(11L, 12L));

        assertThat(result.outcome()).isEqualTo(TallyOutcome.TIE);
        assertThat(result.leaders()).extracting(CandidateTally::name).containsExactly("Jordan Lee", "Priya Nair");
        assertThat(result.totalVotes()).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-14: a tie between some candidates does not include the candidates behind them")
    void partialTieListsOnlyTheLeaders() {
        TallyResult result = TallyCalculator.tally(CANDIDATES, List.of(11L, 13L, 12L, 13L, 11L));

        assertThat(result.outcome()).isEqualTo(TallyOutcome.TIE);
        assertThat(result.leaders()).extracting(CandidateTally::candidateId).containsExactly(11L, 13L);
    }

    @Test
    @DisplayName("FR-14: without votes there is no winner, and every candidate is listed with zero")
    void noVotesMeansNoWinner() {
        TallyResult result = TallyCalculator.tally(CANDIDATES, List.of());

        assertThat(result.outcome()).isEqualTo(TallyOutcome.NO_VOTES);
        assertThat(result.leaders()).isEmpty();
        assertThat(result.totalVotes()).isZero();
        assertThat(result.rows()).hasSize(3).allSatisfy(row -> {
            assertThat(row.votes()).isZero();
            assertThat(row.percent()).isZero();
        });
    }

    @Test
    @DisplayName("FR-14: a single ballot gives its candidate all of the votes")
    void singleBallot() {
        TallyResult result = TallyCalculator.tally(CANDIDATES, List.of(13L));

        assertThat(result.outcome()).isEqualTo(TallyOutcome.WINNER);
        assertThat(result.leaders()).extracting(CandidateTally::name).containsExactly("Marcus Hill");
        assertThat(result.rows().get(2).percent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("FR-14: rows keep the ballot order of the candidates, not the order of the votes")
    void rowsKeepCandidateOrder() {
        TallyResult result = TallyCalculator.tally(CANDIDATES, List.of(13L, 13L, 12L));

        assertThat(result.rows()).extracting(CandidateTally::candidateId).containsExactly(11L, 12L, 13L);
    }

    @Test
    @DisplayName("NFR-02: a ballot for a candidate that is not on the ballot is an integrity error, not a silent skip")
    void unknownCandidateIsAnIntegrityError() {
        assertThatThrownBy(() -> TallyCalculator.tally(CANDIDATES, List.of(11L, 99L)))
                .isInstanceOf(IllegalStateException.class);
        List<Long> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> TallyCalculator.tally(CANDIDATES, withNull))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("A candidate list that names the same candidate twice is refused")
    void duplicateCandidateIsRefused() {
        assertThatThrownBy(() -> TallyCalculator.tally(List.of(JORDAN, JORDAN), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} of {1} votes -> {2}%")
    @CsvSource({"1, 3, 33.3", "2, 3, 66.7", "1, 8, 12.5", "1, 6, 16.7", "5, 5, 100.0", "0, 5, 0.0", "0, 0, 0.0"})
    @DisplayName("FR-14: shares are percentages rounded to one decimal place")
    void percentRounding(long votes, long total, double expected) {
        assertThat(TallyCalculator.percent(votes, total)).isEqualTo(expected);
    }
}
