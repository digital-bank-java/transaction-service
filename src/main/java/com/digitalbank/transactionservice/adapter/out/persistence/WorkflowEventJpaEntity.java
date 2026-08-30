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
                status);
    }

    String eventId() { return eventId; }

    void markProcessed() { status = EventStatus.PROCESSED; }

    UUID transferId() { return transferId; }

    EventStatus status() { return status; }
}
