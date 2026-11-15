package edu.comp5100.election.property;

import static org.assertj.core.api.Assertions.assertThat;

import edu.comp5100.election.policy.ReceiptCodec;
import java.util.Arrays;
import java.util.Locale;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.Size;

/** Property-based tests of confirmation code encoding (FR-12, FR-13), run by the jqwik engine. */
@Label("Receipt codec properties (jqwik)")
class ReceiptCodecProperties {

    @Property
    @Label("Any 20 bytes encode to 32 characters of the code alphabet")
    void encodingHasTheCanonicalShape(@ForAll @Size(20) byte[] randomBytes) {
        assertThat(ReceiptCodec.encode(randomBytes)).matches("[0-9A-HJKMNP-TV-Z]{32}");
    }

    @Property
    @Label("Round trip: the displayed code, in upper or lower case, normalises back to the canonical code")
    void displayedCodeNormalisesBack(@ForAll @Size(20) byte[] randomBytes) {
        String canonical = ReceiptCodec.encode(randomBytes);
        String displayed = ReceiptCodec.format(canonical);

        assertThat(displayed).hasSize(39);
        assertThat(ReceiptCodec.normalize(displayed)).contains(canonical);
        assertThat(ReceiptCodec.normalize(displayed.toLowerCase(Locale.ROOT))).contains(canonical);
        assertThat(ReceiptCodec.normalize(displayed.replace('-', ' '))).contains(canonical);
    }

    @Property
    @Label("Different random bytes never give the same code or the same stored hash")
    void encodingAndHashAreInjective(@ForAll @Size(20) byte[] first, @ForAll @Size(20) byte[] second) {
        Assume.that(!Arrays.equals(first, second));

        String firstCode = ReceiptCodec.encode(first);
        String secondCode = ReceiptCodec.encode(second);

        assertThat(firstCode).isNotEqualTo(secondCode);
        assertThat(ReceiptCodec.hash(firstCode)).isNotEqualTo(ReceiptCodec.hash(secondCode));
    }

    @Property
    @Label("The stored hash is 64 hexadecimal characters and never contains the code")
    void hashDoesNotRevealTheCode(@ForAll @Size(20) byte[] randomBytes) {
        String canonical = ReceiptCodec.encode(randomBytes);

        assertThat(ReceiptCodec.hash(canonical)).matches("[0-9a-f]{64}").doesNotContain(canonical);
    }
}
