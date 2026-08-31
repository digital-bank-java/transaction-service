package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.domain.TransferStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** The versioned event contract emitted when a transfer workflow is accepted. */
public record TransferCreatedEvent(
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
        String postingRequestId,
        TransferStatus status) {

    public static final String EVENT_TYPE = "TransferCreated.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "transaction-service";

    public static TransferCreatedEvent from(Transfer transfer) {
        var eventId = UUID.nameUUIDFromBytes(
                (EVENT_TYPE + ":" + transfer.id()).getBytes(StandardCharsets.UTF_8));
        return new TransferCreatedEvent(
                eventId,
                EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                Instant.now(),
                transfer.id(),
                transfer.correlationId(),
                transfer.transferRequestId(),
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount().toPlainString(),
                transfer.currency(),
                transfer.transferRequestId(),
                transfer.reservationRequestId(),
                transfer.postingRequestId(),
                transfer.status());
    }
}
