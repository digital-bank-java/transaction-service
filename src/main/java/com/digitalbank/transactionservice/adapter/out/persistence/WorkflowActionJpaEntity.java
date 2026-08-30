package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.ReleaseAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestLedgerPosting;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_workflow_actions")
class WorkflowActionJpaEntity {

    @Id
    @Column(name = "action_id", nullable = false, length = 200)
    private String actionId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "action_type", nullable = false, length = 60)
    private String actionType;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "source_account_id")
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "reservation_request_id", length = 100)
    private String reservationRequestId;

    @Column(name = "posting_request_id", length = 100)
    private String postingRequestId;

    @Column(name = "reservation_id", length = 100)
    private String reservationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkflowActionJpaEntity() {}

    WorkflowActionJpaEntity(WorkflowAction action) {
        this.actionId = action.actionId();
        this.transferId = action.transferId();
        this.correlationId = action.correlationId();
        this.createdAt = Instant.now();
        if (action instanceof RequestAccountReservation request) {
            actionType = "REQUEST_ACCOUNT_RESERVATION";
            sourceAccountId = request.sourceAccountId();
            amount = request.amount();
            currency = request.currency();
            requestId = request.reservationRequestId();
            reservationRequestId = request.reservationRequestId();
        } else if (action instanceof RequestLedgerPosting request) {
            actionType = "REQUEST_LEDGER_POSTING";
            sourceAccountId = request.sourceAccountId();
            destinationAccountId = request.destinationAccountId();
            amount = request.amount();
            currency = request.currency();
            requestId = request.postingRequestId();
            postingRequestId = request.postingRequestId();
            reservationId = request.reservationId();
        } else if (action instanceof ReleaseAccountReservation release) {
            actionType = "RELEASE_ACCOUNT_RESERVATION";
            requestId = release.actionId();
            reservationId = release.reservationId();
        } else {
            throw new IllegalArgumentException("Unsupported workflow action: " + action.getClass());
        }
    }

    String actionId() { return actionId; }

    UUID transferId() { return transferId; }
}
