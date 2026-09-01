package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.util.UUID;

public record RequestLedgerPosting(
        String actionId,
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String correlationId,
        String postingRequestId,
        String reservationId) implements WorkflowAction {

    public static RequestLedgerPosting forTransfer(Transfer transfer) {
        return new RequestLedgerPosting(
                "ledger-posting:" + transfer.postingRequestId(),
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount(),
                transfer.currency(),
                transfer.correlationId(),
                transfer.postingRequestId(),
                transfer.reservationId());
    }
}
