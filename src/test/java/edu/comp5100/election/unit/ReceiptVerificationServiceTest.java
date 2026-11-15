package edu.comp5100.election.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import edu.comp5100.election.policy.ReceiptCodec;
import edu.comp5100.election.repository.BallotRepository;
import edu.comp5100.election.service.ReceiptStatus;
import edu.comp5100.election.service.ReceiptVerificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for receipt verification (FR-13). The repository is the only mocked boundary. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Receipt verification service")
class ReceiptVerificationServiceTest {

    private static final String CANONICAL = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Mock
    private BallotRepository ballotRepository;

    @InjectMocks
    private ReceiptVerificationService service;

    @Test
    @DisplayName("FR-13: a code whose hash is stored is reported as accepted, however it is typed")
    void storedCodeIsAccepted() {
        when(ballotRepository.existsByReceiptHash(ReceiptCodec.hash(CANONICAL))).thenReturn(true);

        assertThat(service.verify("0123-4567-89ab-cdef-ghjk-mnpq-rstv-wxyz")).isEqualTo(ReceiptStatus.ACCEPTED);
        verify(ballotRepository).existsByReceiptHash(ReceiptCodec.hash(CANONICAL));
    }

    @Test
    @DisplayName("FR-13: a well-formed code that is not stored is reported as not found")
    void unknownCodeIsNotFound() {
        when(ballotRepository.existsByReceiptHash(anyString())).thenReturn(false);

        assertThat(service.verify(CANONICAL)).isEqualTo(ReceiptStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("FR-13: malformed text is answered without querying the database")
    void malformedCodeNeverReachesTheDatabase() {
        assertThat(service.verify("not-a-code")).isEqualTo(ReceiptStatus.MALFORMED);
        assertThat(service.verify(null)).isEqualTo(ReceiptStatus.MALFORMED);
        verifyNoInteractions(ballotRepository);
    }
}
