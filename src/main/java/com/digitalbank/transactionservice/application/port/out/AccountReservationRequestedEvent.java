package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record AccountReservationRequestedEvent(
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
        UUID destinationAccountId,
        String amount,
        String currency,
        Instant expiresAt) implements ReservationCommandEvent {

    public static final String EVENT_TYPE = "AccountReservationRequested.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "transaction-service";
    private static final long DEFAULT_RESERVATION_TTL_MINUTES = 5;

    public static AccountReservationRequestedEvent from(Transfer transfer) {
        var eventId = UUID.nameUUIDFromBytes(
                (EVENT_TYPE + ":account-reservation:" + transfer.reservationRequestId())
                        .getBytes(StandardCharsets.UTF_8));
        var occurredAt = Instant.now();
        return new AccountReservationRequestedEvent(
                eventId,
                EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                occurredAt,
                transfer.reservationRequestId(),
                transfer.correlationId(),
                transfer.transferRequestId(),
                transfer.id(),
                transfer.reservationRequestId(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount().toPlainString(),
                transfer.currency(),
                occurredAt.plus(DEFAULT_RESERVATION_TTL_MINUTES, ChronoUnit.MINUTES));
    }

    public BigDecimal amountValue() {
        return new BigDecimal(amount);
    }
}
