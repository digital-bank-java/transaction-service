package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import java.util.Objects;
import java.util.UUID;

public record WorkflowEventRecord(
        String eventId,
        UUID transferId,
        EventType eventType,
        String correlationId,
        String requestId,
        String reservationRequestId,
        String reservationId,
        String postingRequestId,
        String reason,
        EventStatus status) {

    public WorkflowEventRecord {
        eventId = requireText(eventId, "eventId");
        Objects.requireNonNull(transferId, "transferId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        correlationId = requireText(correlationId, "correlationId");
        requestId = requireText(requestId, "requestId");
        reservationRequestId = optionalText(reservationRequestId);
        reservationId = optionalText(reservationId);
        postingRequestId = optionalText(postingRequestId);
        reason = optionalText(reason);
        Objects.requireNonNull(status, "status must not be null");
    }

    public static WorkflowEventRecord from(AccountReservationCreated event) {
        return new WorkflowEventRecord(
                event.eventId(),
                event.transferId(),
                EventType.ACCOUNT_RESERVATION_CREATED,
                event.correlationId(),
                event.reservationRequestId(),
                event.reservationRequestId(),
                event.reservationId(),
                null,
                null,
                EventStatus.PROCESSED);
    }

    public static WorkflowEventRecord from(AccountReservationAccepted event) {
        return new WorkflowEventRecord(
                event.eventId(), event.transferId(), EventType.ACCOUNT_RESERVATION_ACCEPTED,
                event.correlationId(), event.reservationRequestId(), event.reservationRequestId(),
                event.reservationId(), null, null, EventStatus.PROCESSED);
    }

    public static WorkflowEventRecord from(AccountReservationRejected event) {
        return new WorkflowEventRecord(
                event.eventId(),
                event.transferId(),
                EventType.ACCOUNT_RESERVATION_REJECTED,
                event.correlationId(),
                event.reservationRequestId(),
                event.reservationRequestId(),
                null,
                null,
                event.reason(),
                EventStatus.PROCESSED);
    }

    public static WorkflowEventRecord from(AccountReservationReleased event) {
        return new WorkflowEventRecord(
                event.eventId(), event.transferId(), EventType.ACCOUNT_RESERVATION_RELEASED,
                event.correlationId(), event.reservationRequestId(), event.reservationRequestId(),
                event.reservationId(), null, null, EventStatus.PROCESSED);
    }

    public static WorkflowEventRecord from(AccountReservationExpired event) {
        return new WorkflowEventRecord(
                event.eventId(), event.transferId(), EventType.ACCOUNT_RESERVATION_EXPIRED,
                event.correlationId(), event.reservationRequestId(), event.reservationRequestId(),
                event.reservationId(), null, null, EventStatus.PROCESSED);
    }

    public static WorkflowEventRecord from(LedgerPostingCompleted event) {
        return new WorkflowEventRecord(
                event.eventId(),
                event.transferId(),
                EventType.LEDGER_POSTING_COMPLETED,
                event.correlationId(),
                event.requestId(),
                null,
                null,
                event.postingRequestId(),
                null,
                EventStatus.PROCESSED);
    }

    public static WorkflowEventRecord from(LedgerPostingFailed event) {
        return new WorkflowEventRecord(
                event.eventId(),
                event.transferId(),
                EventType.LEDGER_POSTING_FAILED,
                event.correlationId(),
                event.requestId(),
                null,
                null,
                event.postingRequestId(),
                event.reason(),
                EventStatus.PROCESSED);
    }

    public WorkflowEventRecord deferred() {
        return new WorkflowEventRecord(
                eventId,
                transferId,
                eventType,
                correlationId,
                requestId,
                reservationRequestId,
                reservationId,
                postingRequestId,
                reason,
                EventStatus.DEFERRED);
    }

    public WorkflowEventRecord processed() {
        return new WorkflowEventRecord(
                eventId,
                transferId,
                eventType,
                correlationId,
                requestId,
                reservationRequestId,
                reservationId,
                postingRequestId,
                reason,
                EventStatus.PROCESSED);
    }

    public boolean isDeferred() {
        return status == EventStatus.DEFERRED;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum EventType {
        ACCOUNT_RESERVATION_CREATED,
        ACCOUNT_RESERVATION_ACCEPTED,
        ACCOUNT_RESERVATION_REJECTED,
        ACCOUNT_RESERVATION_RELEASED,
        ACCOUNT_RESERVATION_EXPIRED,
        LEDGER_POSTING_COMPLETED,
        LEDGER_POSTING_FAILED
    }

    public enum EventStatus {
        DEFERRED,
        PROCESSED
    }
}
