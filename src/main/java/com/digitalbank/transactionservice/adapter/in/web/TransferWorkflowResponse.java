package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.service.WorkflowResult;
import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

record TransferWorkflowResponse(
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String correlationId,
        String transferRequestId,
        String reservationRequestId,
        String postingRequestId,
        String reservationId,
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
                transfer.correlationId(),
                transfer.transferRequestId(),
                transfer.reservationRequestId(),
                transfer.postingRequestId(),
                transfer.reservationId(),
                transfer.status().name(),
                transfer.version(),
                result.actions().stream().map(TransferWorkflowActionResponse::from).toList());
    }
}
