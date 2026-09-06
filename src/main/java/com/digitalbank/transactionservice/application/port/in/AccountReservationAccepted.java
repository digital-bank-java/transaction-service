package com.digitalbank.transactionservice.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AccountReservationAccepted(
        UUID transferId,
        String eventId,
        String correlationId,
        String reservationRequestId,
        String reservationId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant expiresAt) {

    public AccountReservationAccepted {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        reservationId = requireText(reservationId, "reservationId");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("sourceAccountId and destinationAccountId must differ");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireCurrency(currency);
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireCurrency(String value) {
        var normalized = requireText(value, "currency").toUpperCase();
        if (normalized.length() != 3 || !normalized.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        return normalized;
    }
}
