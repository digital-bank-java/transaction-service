package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.UUID;

public sealed interface LedgerCommandEvent permits LedgerPostingRequestedEvent {

    UUID eventId();

    String eventType();

    String schemaVersion();

    String producer();

    Instant occurredAt();

    String aggregateId();

    String correlationId();

    String causationId();

    UUID transactionId();

    String reservationRequestId();

    String reservationId();

    String postingRequestId();
}
