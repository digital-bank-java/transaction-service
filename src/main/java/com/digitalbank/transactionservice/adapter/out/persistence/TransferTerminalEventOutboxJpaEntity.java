package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_terminal_event_outbox")
class TransferTerminalEventOutboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "schema_version", nullable = false, length = 20)
    private String schemaVersion;

    @Column(name = "producer", nullable = false, length = 100)
    private String producer;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "aggregate_id", nullable = false, unique = true)
    private UUID aggregateId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 150)
    private String causationId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "transfer_request_id", nullable = false, length = 100)
    private String transferRequestId;

    @Column(name = "reservation_request_id", length = 100)
    private String reservationRequestId;

    @Column(name = "reservation_id", length = 100)
    private String reservationId;

    @Column(name = "posting_request_id", length = 100)
    private String postingRequestId;

    @Column(name = "posting_id", length = 150)
    private String postingId;

    @Column(name = "status", nullable = false, length = 40)
    private String transferStatus;

    @Column(name = "failure_stage", length = 40)
    private String failureStage;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "compensation_status", length = 30)
    private String compensationStatus;

    @Column(name = "manual_review_required")
    private Boolean manualReviewRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private TransferTerminalOutboxStatus eventStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "processing_until")
    private Instant processingUntil;

    @Column(name = "json_payload", nullable = false, columnDefinition = "text")
    private String jsonPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransferTerminalEventOutboxJpaEntity() {}

    TransferTerminalEventOutboxJpaEntity(TransferTerminalEvent event, String jsonPayload, Instant now) {
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.schemaVersion = event.schemaVersion();
        this.producer = event.producer();
        this.occurredAt = event.occurredAt();
        this.aggregateId = event.aggregateId();
        this.correlationId = event.correlationId();
        this.causationId = event.causationId();
        this.transactionId = event.transactionId();
        this.sourceAccountId = event.sourceAccountId();
        this.destinationAccountId = event.destinationAccountId();
        this.amount = new BigDecimal(event.amount());
        this.currency = event.currency();
        this.transferRequestId = event.transferRequestId();
        this.reservationRequestId = event.reservationRequestId();
        this.reservationId = event.reservationId();
        this.postingRequestId = event.postingRequestId();
        this.postingId = event.postingId();
        this.transferStatus = event.status();
        this.failureStage = event.failureStage();
        this.failureCode = event.failureCode();
        this.failureReason = event.failureReason();
        this.compensationStatus = event.compensationStatus();
        this.manualReviewRequired = event.manualReviewRequired();
        this.eventStatus = TransferTerminalOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.availableAt = now;
        this.jsonPayload = jsonPayload;
        this.createdAt = now;
    }

    UUID eventId() { return eventId; }

    String eventType() { return eventType; }

    String jsonPayload() { return jsonPayload; }
}
