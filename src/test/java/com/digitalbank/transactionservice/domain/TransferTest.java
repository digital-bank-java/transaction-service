package com.digitalbank.transactionservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransferTest {

    private static final UUID TRANSFER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DESTINATION_ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void requestedTransferAwaitsAccountReservation() {
        var transfer = requestedTransfer();

        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.correlationId()).isEqualTo("transfer-correlation-001");
        assertThat(transfer.reservationRequestId()).isEqualTo("reservation-request-001");
        assertThat(transfer.postingRequestId()).isEqualTo("posting-request-001");
        assertThat(transfer.version()).isZero();
    }

    @Test
    void reservationSuccessMovesTransferToLedgerPosting() {
        var transfer = requestedTransfer();

        transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "transfer-correlation-001");

        assertThat(transfer.status()).isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(transfer.reservationId()).isEqualTo("reservation-001");
        assertThat(transfer.version()).isEqualTo(1);
    }

    @Test
    void ledgerSuccessCompletesTransfer() {
        var transfer = requestedTransfer();
        transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "transfer-correlation-001");

        transfer.ledgerPostingCompleted("posting-request-001", "transfer-correlation-001");

        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.version()).isEqualTo(2);
    }

    @Test
    void ledgerFailureFailsTransfer() {
        var transfer = requestedTransfer();
        transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "transfer-correlation-001");

        transfer.ledgerPostingFailed("posting-request-001", "transfer-correlation-001");

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void completedTransferCanBeReversed() {
        var transfer = requestedTransfer();
        transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "transfer-correlation-001");
        transfer.ledgerPostingCompleted("posting-request-001", "transfer-correlation-001");

        transfer.reverse();

        assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSED);
    }

    @Test
    void illegalTransitionIsRejected() {
        var transfer = requestedTransfer();

        assertThatThrownBy(() -> transfer.ledgerPostingCompleted("posting-request-001", "transfer-correlation-001"))
                .isInstanceOf(IllegalTransferTransitionException.class)
                .hasMessage("Cannot transition transfer from PENDING to COMPLETED");
    }

    @Test
    void repeatedReservationEventIsIdempotent() {
        var transfer = requestedTransfer();
        transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "transfer-correlation-001");

        transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "transfer-correlation-001");

        assertThat(transfer.status()).isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(transfer.version()).isEqualTo(1);
    }

    @Test
    void reservationCorrelationMismatchIsRejectedWithoutMutation() {
        var transfer = requestedTransfer();

        assertThatThrownBy(() -> transfer.accountReservationCreated(
                "reservation-request-001", "reservation-001", "other-correlation"))
                .isInstanceOf(TransferConflictException.class);
        assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
        assertThat(transfer.version()).isZero();
    }

    @Test
    void failedTransferCannotComplete() {
        var transfer = requestedTransfer();
        transfer.accountReservationRejected();

        assertThatThrownBy(() -> transfer.ledgerPostingCompleted("posting-request-001", "transfer-correlation-001"))
                .isInstanceOf(IllegalTransferTransitionException.class);
    }

    private static Transfer requestedTransfer() {
        return Transfer.request(
                TRANSFER_ID,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal("12.50"),
                "AED",
                "transfer-correlation-001",
                "transfer-request-001",
                "reservation-request-001",
                "posting-request-001");
    }
}
