package com.digitalbank.transactionservice.application.port.out;

import java.util.UUID;

public sealed interface WorkflowAction
        permits RequestAccountReservation, RequestLedgerPosting, ReleaseAccountReservation {

    String actionId();

    UUID transferId();

    String correlationId();
}
