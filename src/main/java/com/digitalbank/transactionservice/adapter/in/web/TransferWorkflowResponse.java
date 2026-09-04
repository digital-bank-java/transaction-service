package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.service.WorkflowResult;
import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.risk.TransferDestinationClass;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record TransferWorkflowResponse(
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String customerId,
        String channel,
        TransferDestinationClass destinationClass,
        String correlationId,
        String transferRequestId,
        String reservationRequestId,
        String postingRequestId,
        String reservationId,
        UUID riskDecisionId,
        String riskDecisionRequestId,
        String riskOutcome,
        List<String> riskReasonCodes,
        String riskRequiredAssurance,
        String riskChallengeType,
        String riskPolicyVersion,
        java.time.Instant riskIssuedAt,
        java.time.Instant riskExpiresAt,
        String status,
        long version,
        List<TransferWorkflowActionResponse> actions) {

    static TransferWorkflowResponse from(WorkflowResult result) {
        var transfer = result.transfer();
        return new TransferWorkflowResponse(
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
                transfer.riskDecision() == null ? null : transfer.riskDecision().decisionId(),
                transfer.riskDecision() == null ? transfer.transferRequestId() : transfer.riskDecision().decisionRequestId(),
                transfer.riskDecision() == null ? null : transfer.riskDecision().outcome().name(),
                transfer.riskDecision() == null ? List.of() : transfer.riskDecision().reasonCodes(),
                transfer.riskDecision() == null ? null : transfer.riskDecision().requiredAssurance(),
                transfer.riskDecision() == null ? null : transfer.riskDecision().challengeType(),
                transfer.riskDecision() == null ? null : transfer.riskDecision().policyVersion(),
                transfer.riskDecision() == null ? null : transfer.riskDecision().issuedAt(),
                transfer.riskDecision() == null ? null : transfer.riskDecision().expiresAt(),
                transfer.status().name(),
                transfer.version(),
                result.actions().stream().map(TransferWorkflowActionResponse::from).toList());
    }
}
