package com.digitalbank.transactionservice.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LedgerPostingCompleted(
        UUID transferId,
        String eventId,
        String correlationId,
        String requestId,
        String postingId,
        String postingRequestId,
        String reservationRequestId,
        String currency,
        List<Line> lines) {

    public LedgerPostingCompleted(
            UUID transferId,
            String eventId,
            String correlationId,
            String requestId,
            String postingRequestId) {
        this(transferId, eventId, correlationId, requestId, null, postingRequestId, null, null, List.of());
    }

    public LedgerPostingCompleted {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        requestId = requireText(requestId, "requestId");
        postingId = optionalText(postingId);
        postingRequestId = requireText(postingRequestId, "postingRequestId");
        reservationRequestId = optionalText(reservationRequestId);
        currency = optionalText(currency);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record Line(UUID accountId, String lineType, BigDecimal amount) {

        public Line {
            Objects.requireNonNull(accountId, "accountId must not be null");
            lineType = requireText(lineType, "lineType");
            if (!List.of("DEBIT", "CREDIT").contains(lineType)) {
                throw new IllegalArgumentException("Unsupported lineType");
            }
            Objects.requireNonNull(amount, "amount must not be null");
        }
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
}
