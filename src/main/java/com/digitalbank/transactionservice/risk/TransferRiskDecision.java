package com.digitalbank.transactionservice.risk;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record TransferRiskDecision(
        UUID decisionId,
        String decisionRequestId,
        UUID transferId,
        TransferRiskOutcome outcome,
        List<String> reasonCodes,
        String requiredAssurance,
        String challengeType,
        String policyVersion,
        Instant issuedAt,
        Instant expiresAt,
        String correlationId) {

    public TransferRiskDecision {
        Objects.requireNonNull(decisionId, "decisionId must not be null");
        if (decisionRequestId == null || decisionRequestId.isBlank()) {
            throw new IllegalArgumentException("decisionRequestId must not be blank");
        }
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        reasonCodes = List.copyOf(reasonCodes == null ? List.of() : reasonCodes);
        policyVersion = requireText(policyVersion, "policyVersion");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        correlationId = requireText(correlationId, "correlationId");
        if (outcome == TransferRiskOutcome.REQUIRE_STEP_UP) {
            requiredAssurance = requireText(requiredAssurance, "requiredAssurance");
            challengeType = requireText(challengeType, "challengeType");
        }
    }

    public boolean expiredAt(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        return !now.isBefore(expiresAt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
