package com.digitalbank.transactionservice.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record LedgerPostingCompleted(
        UUID transferId,
        String eventId,
        String correlationId,
        String requestId,
        String postingRequestId) {

    public LedgerPostingCompleted {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        requestId = requireText(requestId, "requestId");
        postingRequestId = requireText(postingRequestId, "postingRequestId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
