package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent;
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
@Table(name = "reservation_command_event_outbox")
class ReservationCommandEventOutboxJpaEntity {

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

    @Column(name = "aggregate_id", nullable = false, length = 150)
    private String aggregateId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 150)
    private String causationId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "reservation_request_id", nullable = false, length = 100)
    private String reservationRequestId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "reservation_id", length = 100)
    private String reservationId;

    @Column(name = "posting_request_id", length = 100)
    private String postingRequestId;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "reason", length = 100)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false, length = 20)
    private ReservationCommandOutboxStatus eventStatus;

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

    protected ReservationCommandEventOutboxJpaEntity() {}

    ReservationCommandEventOutboxJpaEntity(ReservationCommandEvent event, String jsonPayload, Instant now) {
        eventId = event.eventId();
        eventType = event.eventType();
        schemaVersion = event.schemaVersion();
        producer = event.producer();
        occurredAt = event.occurredAt();
        aggregateId = event.aggregateId();
        correlationId = event.correlationId();
        causationId = event.causationId();
        transactionId = event.transactionId();
        reservationRequestId = event.reservationRequestId();
        sourceAccountId = event.sourceAccountId();
        if (event instanceof com.digitalbank.transactionservice.application.port.out.AccountReservationRequestedEvent requested) {
            destinationAccountId = requested.destinationAccountId();
            amount = requested.amountValue();
            currency = requested.currency();
            expiresAt = requested.expiresAt();
        } else if (event instanceof com.digitalbank.transactionservice.application.port.out.AccountReservationReleaseRequestedEvent release) {
            reservationId = release.reservationId();
            postingRequestId = release.postingRequestId();
            reason = release.reason();
        }
        eventStatus = ReservationCommandOutboxStatus.PENDING;
        attemptCount = 0;
        availableAt = now;
        this.jsonPayload = jsonPayload;
        createdAt = now;
    }

    UUID eventId() { return eventId; }

    String eventType() { return eventType; }

    String jsonPayload() { return jsonPayload; }
}
