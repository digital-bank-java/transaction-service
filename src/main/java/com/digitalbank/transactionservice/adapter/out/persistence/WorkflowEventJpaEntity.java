package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventStatus;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "transfer_workflow_events")
class WorkflowEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, length = 150)
    private String eventId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 60)
    private EventType eventType;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "reservation_request_id", length = 100)
    private String reservationRequestId;

    @Column(name = "reservation_id", length = 100)
    private String reservationId;

    @Column(name = "posting_request_id", length = 100)
    private String postingRequestId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "decision_id")
    private UUID decisionId;

    @Column(name = "subject_id", length = 100)
    private String subjectId;

    @Column(name = "challenge_id", length = 150)
    private String challengeId;

    @Column(name = "assurance_type", length = 50)
    private String assuranceType;

    @Column(name = "challenge_type", length = 50)
    private String challengeType;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "policy_version", length = 100)
    private String policyVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkflowEventJpaEntity() {}

    WorkflowEventJpaEntity(WorkflowEventRecord event) {
        this.eventId = event.eventId();
        this.transferId = event.transferId();
        this.eventType = event.eventType();
        this.correlationId = event.correlationId();
        this.requestId = event.requestId();
        this.reservationRequestId = event.reservationRequestId();
        this.reservationId = event.reservationId();
        this.postingRequestId = event.postingRequestId();
        this.reason = event.reason();
        this.status = event.status();
        this.decisionId = event.decisionId();
        this.subjectId = event.subjectId();
        this.challengeId = event.challengeId();
        this.assuranceType = event.assuranceType();
        this.challengeType = event.challengeType();
        this.sourceAccountId = event.sourceAccountId();
        this.destinationAccountId = event.destinationAccountId();
        this.amount = event.amount();
        this.currency = event.currency();
        this.verifiedAt = event.verifiedAt();
        this.expiresAt = event.expiresAt();
        this.policyVersion = event.policyVersion();
        this.createdAt = Instant.now();
    }

    WorkflowEventRecord toRecord() {
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
                status,
                decisionId,
                subjectId,
                challengeId,
                assuranceType,
                challengeType,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                verifiedAt,
                expiresAt,
                policyVersion);
    }

    String eventId() { return eventId; }

    void markProcessed() { status = EventStatus.PROCESSED; }

    UUID transferId() { return transferId; }

    EventStatus status() { return status; }
}
