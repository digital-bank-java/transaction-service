package com.digitalbank.transactionservice.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record LedgerPostingFailed(
        UUID transferId,
        String eventId,
        String correlationId,
        String requestId,
        String postingRequestId,
        String reason) {

    public LedgerPostingFailed {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        requestId = requireText(requestId, "requestId");
        postingRequestId = requireText(postingRequestId, "postingRequestId");
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
