package com.digitalbank.transactionservice.application.port.out;

import java.util.UUID;

public record ReleaseAccountReservation(
        String actionId,
        UUID transferId,
        String correlationId,
        String reservationId) implements WorkflowAction {

    public static ReleaseAccountReservation forTransfer(UUID transferId, String correlationId, String reservationId) {
        return new ReleaseAccountReservation(
                "release-reservation:" + reservationId,
                transferId,
                correlationId,
                reservationId);
    }
}
