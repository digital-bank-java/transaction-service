package com.digitalbank.transactionservice.application.port.in;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record RequestTransferCommand(
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String correlationId,
        String transferRequestId,
        String reservationRequestId,
        String postingRequestId) {

    public RequestTransferCommand {
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireCurrency(currency);
        correlationId = requireText(correlationId, "correlationId");
        transferRequestId = requireText(transferRequestId, "transferRequestId");
        reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        postingRequestId = requireText(postingRequestId, "postingRequestId");
    }

    private static String requireCurrency(String value) {
        var normalized = requireText(value, "currency").toUpperCase();
        if (normalized.length() != 3) {
            throw new IllegalArgumentException("currency must have length 3");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
