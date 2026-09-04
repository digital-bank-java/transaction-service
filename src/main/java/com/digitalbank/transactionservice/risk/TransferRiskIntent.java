package com.digitalbank.transactionservice.risk;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record TransferRiskIntent(
        UUID transferId,
        String decisionRequestId,
        String customerId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String channel,
        TransferDestinationClass destinationClass,
        String correlationId) {

    public TransferRiskIntent {
        Objects.requireNonNull(transferId, "transferId must not be null");
        decisionRequestId = requireText(decisionRequestId, "decisionRequestId");
        customerId = requireText(customerId, "customerId");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("sourceAccountId and destinationAccountId must differ");
        }
        Objects.requireNonNull(amount, "amount must not be null");
        amount = amount.stripTrailingZeros();
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireText(currency, "currency").toUpperCase();
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must have length 3");
        }
        channel = requireText(channel, "channel").toUpperCase();
        Objects.requireNonNull(destinationClass, "destinationClass must not be null");
        correlationId = requireText(correlationId, "correlationId");
    }

    String canonicalForm() {
        return String.join(
                "|",
                transferId.toString(),
                decisionRequestId,
                customerId,
                sourceAccountId.toString(),
                destinationAccountId.toString(),
                amount.toPlainString(),
                currency,
                channel,
                destinationClass.name(),
                correlationId);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
