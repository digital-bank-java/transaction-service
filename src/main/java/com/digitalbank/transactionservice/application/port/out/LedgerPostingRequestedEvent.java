package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public record LedgerPostingRequestedEvent(
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
        String reservationId,
        String postingRequestId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        String amount,
        String currency) implements LedgerCommandEvent {

    public static final String EVENT_TYPE = "LedgerPostingRequested.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "transaction-service";

    public static LedgerPostingRequestedEvent from(Transfer transfer) {
        return new LedgerPostingRequestedEvent(
                UUID.nameUUIDFromBytes((EVENT_TYPE + ":ledger-posting:" + transfer.postingRequestId())
                        .getBytes(StandardCharsets.UTF_8)),
                EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                Instant.now(),
                transfer.postingRequestId(),
                transfer.correlationId(),
                transfer.reservationRequestId(),
                transfer.id(),
                transfer.reservationRequestId(),
                transfer.reservationId(),
                transfer.postingRequestId(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount().toPlainString(),
                transfer.currency());
    }

    public BigDecimal amountValue() {
        return new BigDecimal(amount);
    }
}
