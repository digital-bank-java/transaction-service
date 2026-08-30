package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.port.out.ReleaseAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestLedgerPosting;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import java.math.BigDecimal;
import java.util.UUID;

record TransferWorkflowActionResponse(
        String actionId,
        String type,
        UUID transferId,
        String correlationId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String reservationRequestId,
        String postingRequestId,
        String reservationId) {

    static TransferWorkflowActionResponse from(WorkflowAction action) {
        return switch (action) {
            case RequestAccountReservation request -> new TransferWorkflowActionResponse(
                    request.actionId(),
                    "REQUEST_ACCOUNT_RESERVATION",
                    request.transferId(),
                    request.correlationId(),
                    request.sourceAccountId(),
                    null,
                    request.amount(),
                    request.currency(),
                    request.reservationRequestId(),
                    null,
                    null);
            case RequestLedgerPosting request -> new TransferWorkflowActionResponse(
                    request.actionId(),
                    "REQUEST_LEDGER_POSTING",
                    request.transferId(),
                    request.correlationId(),
                    request.sourceAccountId(),
                    request.destinationAccountId(),
                    request.amount(),
                    request.currency(),
                    null,
                    request.postingRequestId(),
                    request.reservationId());
            case ReleaseAccountReservation request -> new TransferWorkflowActionResponse(
                    request.actionId(),
                    "RELEASE_ACCOUNT_RESERVATION",
                    request.transferId(),
                    request.correlationId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    request.reservationId());
        };
    }
}
