package com.digitalbank.transactionservice.application.port.in;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MfaAssuranceGranted(
        UUID transferId,
        String eventId,
        String correlationId,
        String requestId,
        String reservationRequestId,
        UUID decisionId,
        String customerId,
        String challengeId,
        String assuranceType,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String challengeType,
        Instant grantedAt,
        Instant expiresAt,
        String policyVersion) {

    public MfaAssuranceGranted {
        Objects.requireNonNull(transferId, "transferId must not be null");
        eventId = requireText(eventId, "eventId");
        correlationId = requireText(correlationId, "correlationId");
        requestId = requireText(requestId, "requestId");
        Objects.requireNonNull(decisionId, "decisionId must not be null");
        reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        customerId = requireText(customerId, "customerId");
        challengeId = requireText(challengeId, "challengeId");
        assuranceType = requireText(assuranceType, "assuranceType");
        if (!"MFA".equals(assuranceType)) {
            throw new IllegalArgumentException("assuranceType must be MFA");
        }
        challengeType = requireText(challengeType, "challengeType");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        currency = requireText(currency, "currency").toUpperCase();
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO-4217 code");
        }
        Objects.requireNonNull(grantedAt, "grantedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!grantedAt.isBefore(expiresAt)) {
            throw new IllegalArgumentException("grantedAt must be before expiresAt");
        }
        policyVersion = requireText(policyVersion, "policyVersion");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
