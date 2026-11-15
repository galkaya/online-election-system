package edu.comp5100.election.adversarial.unit;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.domain.ElectionStatus;
import edu.comp5100.election.policy.ElectionLifecyclePolicy;
import edu.comp5100.election.policy.ElectionPhase;
import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.policy.TallyCalculator;
import edu.comp5100.election.policy.VoteContext;
import edu.comp5100.election.policy.VoteRejectionReason;
import edu.comp5100.election.policy.VotingPolicy;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * AI-assisted adversarial suite, candidates C04, C24 and C26
 * (docs/ai-testing/03-candidate-decisions.md): extreme boundaries and defensive guards of the
 * pure rules. Expected values come from the requirements and from hand-computed bit patterns,
 * not from running the production code.
 */
@Tag("adversarial")
@DisplayName("Adversarial: extreme boundaries and guards of the pure rules")
class BoundaryAndGuardTest {

    private static final Instant START = Instant.parse("2026-11-19T15:00:00Z");

    private static byte[] filled(int value) {
        byte[] bytes = new byte[ReceiptCodec.RANDOM_BYTES];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }

    @Test
    @DisplayName("FR-07 (C04): in the smallest possible window, one nanosecond, only the start instant is accepted")
    void oneNanosecondWindow() {
        Instant end = START.plusNanos(1);

        assertThat(ElectionLifecyclePolicy.isValidWindow(START, end)).isTrue();
        assertThat(ElectionLifecyclePolicy.isWithinVotingWindow(START, end, START.minusNanos(1))).isFalse();
        assertThat(ElectionLifecyclePolicy.isWithinVotingWindow(START, end, START)).isTrue();
        assertThat(ElectionLifecyclePolicy.isWithinVotingWindow(START, end, end)).isFalse();
        assertThat(ElectionLifecyclePolicy.phaseOf(ElectionStatus.OPEN, START, end, START))
                .isEqualTo(ElectionPhase.VOTING);
        assertThat(ElectionLifecyclePolicy.phaseOf(ElectionStatus.OPEN, START, end, end))
                .isEqualTo(ElectionPhase.ENDED);

        VoteContext atEnd = new VoteContext(true, true, ElectionStatus.OPEN, START, end, end, false, true, true);
        assertThat(VotingPolicy.evaluate(atEnd)).contains(VoteRejectionReason.VOTING_ENDED);
        VoteContext atStart = new VoteContext(true, true, ElectionStatus.OPEN, START, end, START, false, true, true);
        assertThat(VotingPolicy.evaluate(atStart)).isEmpty();
    }

    @Test
    @DisplayName("FR-12 (C24): dense bit patterns encode correctly across every byte boundary")
    void denseBitPatternsEncodeCorrectly() {
        // 0xAA = 10101010: the 160-bit stream alternates 1,0,1,0,... Five-bit groups are therefore
        // 10101 (21, 'N') and 01010 (10, 'A') in turn. 0x55 is the inverse stream.
        assertThat(ReceiptCodec.encode(filled(0xAA))).isEqualTo("NA".repeat(16));
        assertThat(ReceiptCodec.encode(filled(0x55))).isEqualTo("AN".repeat(16));
        // 0x0F = 00001111: groups repeat every 8 characters: 00001 11100 00111 10000 11110 00011 11000 01111.
        assertThat(ReceiptCodec.encode(filled(0x0F))).isEqualTo("1W7GY3RF".repeat(4));
    }

    @Test
    @DisplayName("FR-13 (C24): dense codes survive the display and normalisation round trip")
    void denseCodesRoundTrip() {
        String canonical = ReceiptCodec.encode(filled(0xAA));
        assertThat(ReceiptCodec.format(canonical)).isEqualTo("NANA-NANA-NANA-NANA-NANA-NANA-NANA-NANA");
        assertThat(ReceiptCodec.normalize("nana nana nana nana nana nana nana nana")).contains(canonical);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 50, Long.MAX_VALUE})
    @DisplayName("FR-14 (C26): a share is never NaN, infinite or non-zero when there are no votes in total")
    void percentWithoutTotalIsZero(long votes) {
        double percent = TallyCalculator.percent(votes, 0);

        assertThat(percent).isZero();
        assertThat(Double.isFinite(percent)).isTrue();
    }
}
