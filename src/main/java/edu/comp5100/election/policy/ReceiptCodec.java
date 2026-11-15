package edu.comp5100.election.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Encoding, normalising and hashing of confirmation codes (FR-12, FR-13). A code is 160 random
 * bits written as 32 characters of Crockford Base32, shown in groups of four. Only the SHA-256
 * hash of the code is stored.
 */
public final class ReceiptCodec {

    /** 20 bytes = 160 bits of randomness, above the 128-bit minimum. */
    public static final int RANDOM_BYTES = 20;
    public static final int CODE_LENGTH = 32;
    private static final int GROUP_SIZE = 4;
    /** Crockford Base32: no I, L, O or U, so codes are easier to read and type. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private ReceiptCodec() {
    }

    /** Turns exactly 20 random bytes into the 32-character canonical code. */
    public static String encode(byte[] randomBytes) {
        if (randomBytes == null || randomBytes.length != RANDOM_BYTES) {
            throw new IllegalArgumentException("A receipt code needs exactly " + RANDOM_BYTES + " random bytes.");
        }
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        int buffer = 0;
        int bitsInBuffer = 0;
        for (byte value : randomBytes) {
            buffer = (buffer << 8) | (value & 0xFF);
            bitsInBuffer += 8;
            while (bitsInBuffer >= 5) {
                bitsInBuffer -= 5;
                code.append(ALPHABET.charAt((buffer >> bitsInBuffer) & 31));
            }
            buffer &= (1 << bitsInBuffer) - 1;
        }
        return code.toString();
    }

    /** Display form: XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX. */
    public static String format(String canonicalCode) {
        StringBuilder formatted = new StringBuilder(CODE_LENGTH + CODE_LENGTH / GROUP_SIZE);
        for (int index = 0; index < canonicalCode.length(); index++) {
            if (index > 0 && index % GROUP_SIZE == 0) {
                formatted.append('-');
            }
            formatted.append(canonicalCode.charAt(index));
        }
        return formatted.toString();
    }

    /**
     * Accepts what a person may type: lower case, spaces, dashes, and the look-alike letters
     * O (read as 0) and I or L (read as 1). Returns the canonical code, or empty when the input
     * cannot be a receipt code.
     */
    public static Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        StringBuilder canonical = new StringBuilder(CODE_LENGTH);
        for (char raw : input.toCharArray()) {
            if (raw == '-' || Character.isWhitespace(raw)) {
                continue;
            }
            char upper = Character.toUpperCase(raw);
            if (upper == 'O') {
                upper = '0';
            } else if (upper == 'I' || upper == 'L') {
                upper = '1';
            }
            if (ALPHABET.indexOf(upper) < 0) {
                return Optional.empty();
            }
            canonical.append(upper);
            if (canonical.length() > CODE_LENGTH) {
                return Optional.empty();
            }
        }
        if (canonical.length() != CODE_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(canonical.toString());
    }

    /** Lower-case hexadecimal SHA-256 of the canonical code: 64 characters. */
    public static String hash(String canonicalCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalCode.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime.", impossible);
        }
    }
}
