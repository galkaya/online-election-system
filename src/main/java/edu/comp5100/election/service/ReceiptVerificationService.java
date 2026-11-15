package edu.comp5100.election.service;

import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.repository.BallotRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks whether a confirmation code belongs to a stored ballot (FR-13). */
@Service
public class ReceiptVerificationService {

    private final BallotRepository ballotRepository;

    public ReceiptVerificationService(BallotRepository ballotRepository) {
        this.ballotRepository = ballotRepository;
    }

    @Transactional(readOnly = true)
    public ReceiptStatus verify(String enteredCode) {
        Optional<String> canonical = ReceiptCodec.normalize(enteredCode);
        if (canonical.isEmpty()) {
            return ReceiptStatus.MALFORMED;
        }
        boolean stored = ballotRepository.existsByReceiptHash(ReceiptCodec.hash(canonical.get()));
        return stored ? ReceiptStatus.ACCEPTED : ReceiptStatus.NOT_FOUND;
    }
}
