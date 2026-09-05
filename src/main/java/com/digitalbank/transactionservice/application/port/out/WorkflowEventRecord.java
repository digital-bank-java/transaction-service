package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.digitalbank.transactionservice.application.port.in.MfaAssuranceGranted;
import java.math.BigDecimal;
import java.time.Instant;
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
        EventStatus status,
        UUID decisionId,
        String subjectId,
        String challengeId,
        String assuranceType,
        String challengeType,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant verifiedAt,
        Instant expiresAt,
        String policyVersion) {

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
        currency = optionalText(currency);
        subjectId = optionalText(subjectId);
        challengeId = optionalText(challengeId);
        assuranceType = optionalText(assuranceType);
        challengeType = optionalText(challengeType);
        policyVersion = optionalText(policyVersion);
    }

    public WorkflowEventRecord(
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
        this(eventId, transferId, eventType, correlationId, requestId, reservationRequestId, reservationId,
                postingRequestId, reason, status, null, null, null, null, null, null, null, null, null, null, null, null);
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

    public static WorkflowEventRecord from(MfaAssuranceGranted event) {
        return new WorkflowEventRecord(
                event.eventId(), event.transferId(), EventType.MFA_ASSURANCE_GRANTED,
                event.correlationId(), event.requestId(), event.reservationRequestId(), null, null, null,
                EventStatus.PROCESSED, event.decisionId(), event.customerId(), event.challengeId(),
                event.assuranceType(), event.challengeType(), event.sourceAccountId(), event.destinationAccountId(), event.amount(),
                event.currency(), event.grantedAt(), event.expiresAt(), event.policyVersion());
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
        var debitLine = event.lines().stream()
                .filter(line -> line.lineType().equals("DEBIT"))
                .findFirst()
                .orElse(null);
        var creditLine = event.lines().stream()
                .filter(line -> line.lineType().equals("CREDIT"))
                .findFirst()
                .orElse(null);
        return new WorkflowEventRecord(
                event.eventId(),
                event.transferId(),
                EventType.LEDGER_POSTING_COMPLETED,
                event.correlationId(),
                event.requestId(),
                event.reservationRequestId(),
                null,
                event.postingRequestId(),
                null,
                EventStatus.PROCESSED,
                null,
                null,
                null,
                null,
                null,
                debitLine == null ? null : debitLine.accountId(),
                creditLine == null ? null : creditLine.accountId(),
                debitLine == null ? null : debitLine.amount(),
                event.currency(),
                null,
                null,
                null);
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
                EventStatus.DEFERRED,
                decisionId, subjectId, challengeId, assuranceType, challengeType, sourceAccountId, destinationAccountId,
                amount, currency, verifiedAt, expiresAt, policyVersion);
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
                EventStatus.PROCESSED,
                decisionId, subjectId, challengeId, assuranceType, challengeType, sourceAccountId, destinationAccountId,
                amount, currency, verifiedAt, expiresAt, policyVersion);
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
        LEDGER_POSTING_FAILED,
        MFA_ASSURANCE_GRANTED
    }

    public enum EventStatus {
        DEFERRED,
        PROCESSED
    }
}
