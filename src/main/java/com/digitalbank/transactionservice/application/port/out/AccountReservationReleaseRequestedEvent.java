package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record AccountReservationReleaseRequestedEvent(
        UUID eventId,
        String eventType,
        String schemaVersion,
        String producer,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String causationId,
        UUID transactionId,
        String reservationRequestId,
        UUID sourceAccountId,
        String reservationId,
        String reason,
        String postingRequestId) implements ReservationCommandEvent {

    public static final String EVENT_TYPE = "AccountReservationReleaseRequested.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "transaction-service";

    public static AccountReservationReleaseRequestedEvent from(Transfer transfer) {
        var eventId = UUID.nameUUIDFromBytes(
                (EVENT_TYPE + ":release-reservation:" + transfer.reservationId())
                        .getBytes(StandardCharsets.UTF_8));
        return new AccountReservationReleaseRequestedEvent(
                eventId,
                EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                Instant.now(),
                transfer.reservationRequestId(),
                transfer.correlationId(),
                transfer.postingRequestId(),
                transfer.id(),
                transfer.reservationRequestId(),
                transfer.sourceAccountId(),
                transfer.reservationId(),
                "LEDGER_POSTING_FAILED",
                transfer.postingRequestId());
    }
}
