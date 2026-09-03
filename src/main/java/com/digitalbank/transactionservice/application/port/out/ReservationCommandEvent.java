package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.UUID;

public sealed interface ReservationCommandEvent
        permits AccountReservationRequestedEvent, AccountReservationReleaseRequestedEvent {

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

    UUID sourceAccountId();
}
