package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.risk.TransferRiskDecision;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

final class TransferWorkflowJpaMapper {

    private TransferWorkflowJpaMapper() {}

    static TransferWorkflowJpaEntity newEntity(Transfer transfer) {
        return new TransferWorkflowJpaEntity(
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount(),
                transfer.currency(),
                transfer.customerId(),
                transfer.channel(),
                transfer.destinationClass(),
                transfer.correlationId(),
                transfer.transferRequestId(),
                transfer.reservationRequestId(),
                transfer.postingRequestId(),
                transfer.reservationId(),
                transfer.status(),
                Instant.now(),
                riskDecisionId(transfer),
                riskDecisionRequestId(transfer),
                riskOutcome(transfer),
                riskReasonCodes(transfer),
                riskRequiredAssurance(transfer),
                riskChallengeType(transfer),
                riskPolicyVersion(transfer),
                riskIssuedAt(transfer),
                riskExpiresAt(transfer));
    }

    static Transfer toDomain(TransferWorkflowJpaEntity entity) {
        return Transfer.rehydrate(
                entity.id(),
                entity.sourceAccountId(),
                entity.destinationAccountId(),
                entity.amount(),
                entity.currency(),
                entity.customerId(),
                entity.channel(),
                entity.destinationClass(),
                entity.correlationId(),
                entity.transferRequestId(),
                entity.reservationRequestId(),
                entity.postingRequestId(),
                entity.reservationId(),
                entity.status(),
                entity.version(),
                riskDecision(entity));
    }

    private static TransferRiskDecision riskDecision(TransferWorkflowJpaEntity entity) {
        if (entity.riskDecisionId() == null || entity.riskOutcome() == null) {
            return null;
        }
        return new TransferRiskDecision(
                entity.riskDecisionId(),
                entity.riskDecisionRequestId(),
                entity.id(),
                entity.riskOutcome(),
                parseReasonCodes(entity.riskReasonCodes()),
                entity.riskRequiredAssurance(),
                entity.riskChallengeType(),
                entity.riskPolicyVersion(),
                entity.riskIssuedAt(),
                entity.riskExpiresAt(),
                entity.correlationId());
    }

    private static List<String> parseReasonCodes(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(",")).map(String::trim).filter(reason -> !reason.isBlank()).toList();
    }

    private static java.util.UUID riskDecisionId(Transfer transfer) {
        return transfer.riskDecision() == null ? null : transfer.riskDecision().decisionId();
    }

    private static String riskDecisionRequestId(Transfer transfer) {
        return transfer.riskDecision() == null
                ? transfer.transferRequestId()
                : transfer.riskDecision().decisionRequestId();
    }

    private static com.digitalbank.transactionservice.risk.TransferRiskOutcome riskOutcome(Transfer transfer) {
        return transfer.riskDecision() == null ? com.digitalbank.transactionservice.risk.TransferRiskOutcome.ALLOW
                : transfer.riskDecision().outcome();
    }

    private static String riskReasonCodes(Transfer transfer) {
        return transfer.riskDecision() == null ? null : String.join(",", transfer.riskDecision().reasonCodes());
    }

    private static String riskRequiredAssurance(Transfer transfer) {
        return transfer.riskDecision() == null ? null : transfer.riskDecision().requiredAssurance();
    }

    private static String riskChallengeType(Transfer transfer) {
        return transfer.riskDecision() == null ? null : transfer.riskDecision().challengeType();
    }

    private static String riskPolicyVersion(Transfer transfer) {
        return transfer.riskDecision() == null ? "legacy" : transfer.riskDecision().policyVersion();
    }

    private static Instant riskIssuedAt(Transfer transfer) {
        return transfer.riskDecision() == null ? null : transfer.riskDecision().issuedAt();
    }

    private static Instant riskExpiresAt(Transfer transfer) {
        return transfer.riskDecision() == null ? null : transfer.riskDecision().expiresAt();
    }
}
