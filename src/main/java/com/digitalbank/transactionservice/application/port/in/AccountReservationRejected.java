package com.digitalbank.transactionservice.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record AccountReservationRejected(
        UUID transferId,
        String eventId,
        String correlationId,
        String reservationRequestId,
        String reason) {

    public AccountReservationRejected {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        reason = requireText(reason, "reason");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
