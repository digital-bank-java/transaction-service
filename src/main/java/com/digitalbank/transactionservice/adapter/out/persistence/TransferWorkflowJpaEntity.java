package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.domain.TransferStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_workflows")
class TransferWorkflowJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 100)
    private String correlationId;

    @Column(name = "transfer_request_id", nullable = false, unique = true, length = 100)
    private String transferRequestId;

    @Column(name = "reservation_request_id", nullable = false, unique = true, length = 100)
    private String reservationRequestId;

    @Column(name = "posting_request_id", nullable = false, unique = true, length = 100)
    private String postingRequestId;

    @Column(name = "reservation_id", length = 100)
    private String reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private TransferStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransferWorkflowJpaEntity() {}

    TransferWorkflowJpaEntity(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId,
            String reservationId,
            TransferStatus status,
            Instant now) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.correlationId = correlationId;
        this.transferRequestId = transferRequestId;
        this.reservationRequestId = reservationRequestId;
        this.postingRequestId = postingRequestId;
        this.reservationId = reservationId;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    UUID id() { return id; }

    UUID sourceAccountId() { return sourceAccountId; }

    UUID destinationAccountId() { return destinationAccountId; }

    BigDecimal amount() { return amount; }

    String currency() { return currency; }

    String correlationId() { return correlationId; }

    String transferRequestId() { return transferRequestId; }

    String reservationRequestId() { return reservationRequestId; }

    String postingRequestId() { return postingRequestId; }

    String reservationId() { return reservationId; }

    TransferStatus status() { return status; }

    Long version() { return version; }

    void updateFrom(com.digitalbank.transactionservice.domain.Transfer transfer, Instant now) {
        this.sourceAccountId = transfer.sourceAccountId();
        this.destinationAccountId = transfer.destinationAccountId();
        this.amount = transfer.amount();
        this.currency = transfer.currency();
        this.correlationId = transfer.correlationId();
        this.transferRequestId = transfer.transferRequestId();
        this.reservationRequestId = transfer.reservationRequestId();
        this.postingRequestId = transfer.postingRequestId();
        this.reservationId = transfer.reservationId();
        this.status = transfer.status();
        this.updatedAt = now;
    }
}
