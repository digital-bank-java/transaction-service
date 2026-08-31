package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
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
@Table(name = "transfer_created_event_outbox")
class TransferCreatedEventOutboxJpaEntity {

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

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 100)
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

    @Column(name = "reservation_request_id", nullable = false, length = 100)
    private String reservationRequestId;

    @Column(name = "posting_request_id", nullable = false, length = 100)
    private String postingRequestId;

    @Column(name = "status", nullable = false, length = 40)
    private String transferStatus;

    @Column(name = "event_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TransferCreatedOutboxStatus eventStatus;

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

    protected TransferCreatedEventOutboxJpaEntity() {}

    TransferCreatedEventOutboxJpaEntity(TransferCreatedEvent event, String jsonPayload, Instant now) {
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
        this.postingRequestId = event.postingRequestId();
        this.transferStatus = event.status().name();
        this.eventStatus = TransferCreatedOutboxStatus.PENDING;
        this.attemptCount = 0;
        this.availableAt = now;
        this.jsonPayload = jsonPayload;
        this.createdAt = now;
    }

    UUID eventId() { return eventId; }

    String jsonPayload() { return jsonPayload; }

    void markPublished(Instant now) {
        eventStatus = TransferCreatedOutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    void markFailed(String error, Instant retryAt) {
        eventStatus = TransferCreatedOutboxStatus.FAILED;
        attemptCount++;
        availableAt = retryAt;
        lastError = error;
    }
}
