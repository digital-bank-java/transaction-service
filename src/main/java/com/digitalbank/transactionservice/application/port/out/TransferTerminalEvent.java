package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Common envelope and projection fields for terminal transfer workflow facts. */
public sealed interface TransferTerminalEvent permits TransferCompletedEvent, TransferFailedEvent {

    UUID eventId();

    String eventType();

    String schemaVersion();

    String producer();

    Instant occurredAt();

    UUID aggregateId();

    String correlationId();

    String causationId();

    UUID transactionId();

    UUID sourceAccountId();

    UUID destinationAccountId();

    String amount();

    String currency();

    String transferRequestId();

    String reservationRequestId();

    String reservationId();

    String postingRequestId();

    String postingId();

    String status();

    String failureStage();

    String failureCode();

    String failureReason();

    String compensationStatus();

    Boolean manualReviewRequired();
}
