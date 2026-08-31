package com.digitalbank.transactionservice.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record AccountReservationReleased(
        UUID transferId,
        String eventId,
        String correlationId,
        String reservationRequestId,
        String reservationId) {

    public AccountReservationReleased {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        reservationId = requireText(reservationId, "reservationId");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
