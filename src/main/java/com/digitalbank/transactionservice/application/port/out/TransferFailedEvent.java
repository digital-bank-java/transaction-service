package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable terminal fact emitted after a transfer reaches FAILED. */
public record TransferFailedEvent(
        UUID eventId,
        String eventType,
        String schemaVersion,
        String producer,
        Instant occurredAt,
        UUID aggregateId,
        String correlationId,
        String causationId,
        UUID transactionId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String amount,
        String currency,
        String transferRequestId,
        String reservationRequestId,
        String reservationId,
        String postingRequestId,
        String postingId,
        String status,
        String failureStage,
        String failureCode,
        String failureReason,
        String compensationStatus,
        Boolean manualReviewRequired) implements TransferTerminalEvent {

    public static final String EVENT_TYPE = "TransferFailed.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "transaction-service";

    public TransferFailedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = requireText(eventType, "eventType");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        producer = requireText(producer, "producer");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        correlationId = requireText(correlationId, "correlationId");
        causationId = requireText(causationId, "causationId");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        amount = requireAmount(amount);
        currency = requireText(currency, "currency");
        transferRequestId = requireText(transferRequestId, "transferRequestId");
        reservationRequestId = optionalText(reservationRequestId);
        reservationId = optionalText(reservationId);
        postingRequestId = optionalText(postingRequestId);
        postingId = optionalText(postingId);
        status = requireText(status, "status");
        failureStage = requireText(failureStage, "failureStage");
        failureCode = requireText(failureCode, "failureCode");
        failureReason = requireText(failureReason, "failureReason");
        compensationStatus = requireText(compensationStatus, "compensationStatus");
        Objects.requireNonNull(manualReviewRequired, "manualReviewRequired must not be null");
        if (!EVENT_TYPE.equals(eventType) || !SCHEMA_VERSION.equals(schemaVersion)
                || !PRODUCER.equals(producer) || !"FAILED".equals(status)) {
            throw new IllegalArgumentException("invalid TransferFailed.v1 envelope");
        }
    }

    public static TransferFailedEvent fromReservationRejected(
            Transfer transfer, String causationId, String reason, Instant occurredAt) {
        return base(
                transfer, causationId, occurredAt, "RESERVATION", "RESERVATION_REJECTED", reason,
                "NOT_REQUIRED");
    }

    public static TransferFailedEvent fromReservationExpired(
            Transfer transfer, String causationId, Instant occurredAt) {
        return base(
                transfer, causationId, occurredAt, "RESERVATION", "RESERVATION_EXPIRED",
                "Account reservation expired before ledger posting.", "COMPLETED");
    }

    public static TransferFailedEvent fromCompensatedLedgerFailure(
            Transfer transfer,
            String causationId,
            String ledgerFailureReason,
            Instant occurredAt) {
        return base(
                transfer, causationId, occurredAt, "LEDGER_POSTING", "LEDGER_POSTING_FAILED",
                ledgerFailureReason, "COMPLETED");
    }

    private static TransferFailedEvent base(
            Transfer transfer,
            String causationId,
            Instant occurredAt,
            String failureStage,
            String failureCode,
            String failureReason,
            String compensationStatus) {
        return new TransferFailedEvent(
                eventIdFor(transfer.id()), EVENT_TYPE, SCHEMA_VERSION, PRODUCER, occurredAt, transfer.id(),
                transfer.correlationId(), causationId, transfer.id(), transfer.sourceAccountId(),
                transfer.destinationAccountId(), transfer.amount().toPlainString(), transfer.currency(), transfer.transferRequestId(),
                transfer.reservationRequestId(), transfer.reservationId(), transfer.postingRequestId(), null, "FAILED",
                failureStage, failureCode, failureReason, compensationStatus, false);
    }

    private static UUID eventIdFor(UUID transferId) {
        return UUID.nameUUIDFromBytes((EVENT_TYPE + ":" + transferId).getBytes(StandardCharsets.UTF_8));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireAmount(String value) {
        var amount = requireText(value, "amount");
        if (!amount.matches("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?$")
                || new BigDecimal(amount).signum() <= 0) {
            throw new IllegalArgumentException("amount must be a positive decimal with no more than 4 decimal places");
        }
        return amount;
    }
}
