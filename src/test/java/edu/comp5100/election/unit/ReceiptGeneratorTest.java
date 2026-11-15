package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.service.ReceiptGenerator;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for confirmation code generation (FR-12). */
@DisplayName("Receipt generator")
class ReceiptGeneratorTest {

    private final ReceiptGenerator generator = new ReceiptGenerator();

    @Test
    @DisplayName("FR-12: codes are 32 Crockford Base32 characters")
    void codesHaveTheCanonicalShape() {
        assertThat(generator.newCode()).matches("[0-9A-HJKMNP-TV-Z]{32}");
    }

    @Test
    @DisplayName("FR-12: codes are random, not a counter: 2000 codes are all different and not consecutive")
    void codesDoNotRepeat() {
        Set<String> codes = new HashSet<>();
        String previous = null;
        for (int index = 0; index < 2000; index++) {
            String code = generator.newCode();
            assertThat(codes.add(code)).as("code %s was generated twice", code).isTrue();
            if (previous != null) {
                // A counter would share a long prefix with its predecessor; random codes practically never do.
                assertThat(code.substring(0, 16)).isNotEqualTo(previous.substring(0, 16));
            }
            previous = code;
        }
    }
}
