package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.util.UUID;

public record RequestAccountReservation(
        String actionId,
        UUID transferId,
        UUID sourceAccountId,
        BigDecimal amount,
        String currency,
        String correlationId,
        String reservationRequestId) implements WorkflowAction {

    public static RequestAccountReservation forTransfer(Transfer transfer) {
        return new RequestAccountReservation(
                "account-reservation:" + transfer.reservationRequestId(),
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.amount(),
                transfer.currency(),
                transfer.correlationId(),
                transfer.reservationRequestId());
    }
}
