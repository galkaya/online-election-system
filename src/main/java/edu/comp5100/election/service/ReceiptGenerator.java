package edu.comp5100.election.service;

import edu.comp5100.election.policy.ReceiptCodec;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Creates unpredictable confirmation codes from a cryptographic random source (FR-12). */
@Component
public class ReceiptGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    /** Returns a new canonical 32-character code carrying 160 random bits. */
    public String newCode() {
        byte[] randomBytes = new byte[ReceiptCodec.RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return ReceiptCodec.encode(randomBytes);
    }
}
