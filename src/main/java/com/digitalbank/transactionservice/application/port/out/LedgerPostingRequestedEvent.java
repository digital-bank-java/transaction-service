package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerPostingRequestedEvent(
        UUID eventId,
        String eventType,
        String schemaVersion,
        String producer,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String causationId,
        UUID transactionId,
        String reservationRequestId,
        String reservationId,
        String postingRequestId,
        String description,
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant effectiveAt,
        List<Line> debitLines,
        List<Line> creditLines) implements LedgerCommandEvent {

    public static final String EVENT_TYPE = "LedgerPostingRequested.v1";
    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "transaction-service";
    private static final String POSITIVE_DECIMAL_PATTERN = "^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?$";

    public LedgerPostingRequestedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = requireText(eventType, "eventType");
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        producer = requireText(producer, "producer");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        aggregateId = requireText(aggregateId, "aggregateId");
        correlationId = requireText(correlationId, "correlationId");
        causationId = requireText(causationId, "causationId");
        Objects.requireNonNull(transactionId, "transactionId must not be null");
        reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        reservationId = requireText(reservationId, "reservationId");
        postingRequestId = requireText(postingRequestId, "postingRequestId");
        description = requireText(description, "description");
        currency = requireCurrency(currency);
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        debitLines = requireSingleLine(debitLines, "debitLines");
        creditLines = requireSingleLine(creditLines, "creditLines");

        var debitAmount = debitLines.getFirst().amountValue();
        var creditAmount = creditLines.getFirst().amountValue();
        if (debitAmount.compareTo(creditAmount) != 0) {
            throw new IllegalArgumentException("debitLines and creditLines must balance");
        }
    }

    public static LedgerPostingRequestedEvent from(Transfer transfer) {
        var occurredAt = Instant.now();
        return new LedgerPostingRequestedEvent(
                UUID.nameUUIDFromBytes((EVENT_TYPE + ":ledger-posting:" + transfer.postingRequestId())
                        .getBytes(StandardCharsets.UTF_8)),
                EVENT_TYPE,
                SCHEMA_VERSION,
                PRODUCER,
                occurredAt,
                transfer.postingRequestId(),
                transfer.correlationId(),
                transfer.reservationRequestId(),
                transfer.id(),
                transfer.reservationRequestId(),
                transfer.reservationId(),
                transfer.postingRequestId(),
                "Transfer posting for " + transfer.transferRequestId(),
                transfer.currency(),
                occurredAt,
                List.of(new Line(transfer.sourceAccountId(), transfer.amount().toPlainString())),
                List.of(new Line(transfer.destinationAccountId(), transfer.amount().toPlainString())));
    }

    public BigDecimal amountValue() {
        return debitLines.getFirst().amountValue();
    }

    public UUID sourceAccountId() {
        return debitLines.getFirst().accountId();
    }

    public UUID destinationAccountId() {
        return creditLines.getFirst().accountId();
    }

    public record Line(UUID accountId, String amount) {

        public Line {
            Objects.requireNonNull(accountId, "accountId must not be null");
            amount = requireText(amount, "amount");
            if (!amount.matches(POSITIVE_DECIMAL_PATTERN)) {
                throw new IllegalArgumentException(
                        "amount must be a positive decimal with no more than 4 decimal places");
            }
            if (new BigDecimal(amount).signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }

        public BigDecimal amountValue() {
            return new BigDecimal(amount);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireCurrency(String value) {
        var currency = requireText(value, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        return currency;
    }

    private static List<Line> requireSingleLine(List<Line> lines, String field) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException(field + " must contain one line");
        }
        var copy = List.copyOf(lines);
        if (copy.size() != 1) {
            throw new IllegalArgumentException(field + " must contain exactly one line");
        }
        return copy;
    }
}
