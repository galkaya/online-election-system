package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import edu.comp5100.election.policy.ReceiptCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Unit tests for confirmation code encoding, normalisation and hashing (FR-12, FR-13). */
@DisplayName("Receipt codec")
class ReceiptCodecTest {

    private static final String CANONICAL = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final String FORMATTED = "0123-4567-89AB-CDEF-GHJK-MNPQ-RSTV-WXYZ";

    private static byte[] bytes(int firstByte, int secondByte, int lastByte) {
        byte[] bytes = new byte[ReceiptCodec.RANDOM_BYTES];
        bytes[0] = (byte) firstByte;
        bytes[1] = (byte) secondByte;
        bytes[bytes.length - 1] = (byte) lastByte;
        return bytes;
    }

    @Test
    @DisplayName("FR-12: 20 random bytes become 32 Base32 characters, five bits per character")
    void encodesFiveBitsPerCharacter() {
        assertThat(ReceiptCodec.encode(bytes(0x00, 0x00, 0x00))).isEqualTo("0".repeat(32));
        assertThat(ReceiptCodec.encode(bytes(0x08, 0x00, 0x00))).isEqualTo("1" + "0".repeat(31));
        assertThat(ReceiptCodec.encode(bytes(0xF8, 0x00, 0x00))).isEqualTo("Z" + "0".repeat(31));
        assertThat(ReceiptCodec.encode(bytes(0x00, 0x44, 0x00))).isEqualTo("012" + "0".repeat(29));
        assertThat(ReceiptCodec.encode(bytes(0x00, 0x00, 0x01))).isEqualTo("0".repeat(31) + "1");
    }

    @Test
    @DisplayName("FR-12: all bits set gives the last alphabet character in every position")
    void encodesAllBitsSet() {
        byte[] allOnes = new byte[ReceiptCodec.RANDOM_BYTES];
        java.util.Arrays.fill(allOnes, (byte) 0xFF);
        assertThat(ReceiptCodec.encode(allOnes)).isEqualTo("Z".repeat(32));
    }

    @Test
    @DisplayName("FR-12: a code needs exactly 160 random bits")
    void rejectsWrongAmountOfRandomness() {
        assertThatThrownBy(() -> ReceiptCodec.encode(new byte[19])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceiptCodec.encode(new byte[21])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReceiptCodec.encode(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-12: the display form is eight groups of four separated by dashes")
    void formatsInGroupsOfFour() {
        assertThat(ReceiptCodec.format(CANONICAL)).isEqualTo(FORMATTED);
    }

    @Test
    @DisplayName("FR-13: the display form, lower case and extra spaces all normalise to the canonical code")
    void normalisesWhatPeopleType() {
        assertThat(ReceiptCodec.normalize(FORMATTED)).contains(CANONICAL);
        assertThat(ReceiptCodec.normalize(CANONICAL)).contains(CANONICAL);
        assertThat(ReceiptCodec.normalize(FORMATTED.toLowerCase())).contains(CANONICAL);
        assertThat(ReceiptCodec.normalize("  0123 4567 89AB CDEF GHJK MNPQ RSTV WXYZ \n")).contains(CANONICAL);
    }

    @Test
    @DisplayName("FR-13: look-alike letters O, I and L are read as the digits 0 and 1")
    void mapsLookAlikeLetters() {
        assertThat(ReceiptCodec.normalize("OIL" + "0".repeat(29))).contains("011" + "0".repeat(29));
        assertThat(ReceiptCodec.normalize("oil" + "0".repeat(29))).contains("011" + "0".repeat(29));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "0123-4567",
            "0123456789ABCDEFGHJKMNPQRSTVWXY",
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ0",
            "U123456789ABCDEFGHJKMNPQRSTVWXYZ",
            "!123456789ABCDEFGHJKMNPQRSTVWXYZ",
            "<script>alert(1)</script>",
            "' OR '1'='1"
    })
    @DisplayName("FR-13: text that cannot be a code is rejected")
    void rejectsMalformedInput(String input) {
        assertThat(ReceiptCodec.normalize(input)).isEmpty();
    }

    @Test
    @DisplayName("FR-13: a missing code is rejected")
    void rejectsNull() {
        assertThat(ReceiptCodec.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("NFR-01: the stored value is the SHA-256 hash, 64 lower-case hexadecimal characters")
    void hashesWithSha256() {
        // Published SHA-256 test vector for the ASCII text "abc".
        assertThat(ReceiptCodec.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(ReceiptCodec.hash(CANONICAL)).hasSize(64).matches("[0-9a-f]{64}").isNotEqualTo(CANONICAL);
        assertThat(ReceiptCodec.hash(CANONICAL)).isEqualTo(ReceiptCodec.hash(CANONICAL));
        assertThat(ReceiptCodec.hash(CANONICAL)).isNotEqualTo(ReceiptCodec.hash("1123456789ABCDEFGHJKMNPQRSTVWXYZ"));
    }
}
