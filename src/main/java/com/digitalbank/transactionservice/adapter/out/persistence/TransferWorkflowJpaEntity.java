package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.domain.TransferStatus;
import com.digitalbank.transactionservice.risk.TransferDestinationClass;
import com.digitalbank.transactionservice.risk.TransferRiskOutcome;
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

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "destination_class", nullable = false, length = 20)
    private TransferDestinationClass destinationClass;

    @Column(name = "correlation_id", nullable = false, unique = true, length = 100)
    private String correlationId;

    @Column(name = "transfer_request_id", nullable = false, unique = true, length = 100)
    private String transferRequestId;

    @Column(name = "reservation_request_id", nullable = false, unique = true, length = 100)
    private String reservationRequestId;

    @Column(name = "posting_request_id", nullable = false, unique = true, length = 100)
    private String postingRequestId;

    @Column(name = "risk_decision_id", unique = true)
    private java.util.UUID riskDecisionId;

    @Column(name = "risk_decision_request_id", nullable = false, unique = true, length = 100)
    private String riskDecisionRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_outcome", length = 30)
    private TransferRiskOutcome riskOutcome;

    @Column(name = "risk_reason_codes", length = 500)
    private String riskReasonCodes;

    @Column(name = "risk_required_assurance", length = 30)
    private String riskRequiredAssurance;

    @Column(name = "risk_challenge_type", length = 30)
    private String riskChallengeType;

    @Column(name = "risk_policy_version", length = 100)
    private String riskPolicyVersion;

    @Column(name = "risk_issued_at")
    private Instant riskIssuedAt;

    @Column(name = "risk_expires_at")
    private Instant riskExpiresAt;

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
            String customerId,
            String channel,
            TransferDestinationClass destinationClass,
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId,
            String reservationId,
            TransferStatus status,
            Instant now,
            java.util.UUID riskDecisionId,
            String riskDecisionRequestId,
            TransferRiskOutcome riskOutcome,
            String riskReasonCodes,
            String riskRequiredAssurance,
            String riskChallengeType,
            String riskPolicyVersion,
            Instant riskIssuedAt,
            Instant riskExpiresAt) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.customerId = customerId;
        this.channel = channel;
        this.destinationClass = destinationClass;
        this.correlationId = correlationId;
        this.transferRequestId = transferRequestId;
        this.reservationRequestId = reservationRequestId;
        this.postingRequestId = postingRequestId;
        this.riskDecisionId = riskDecisionId;
        this.riskDecisionRequestId = riskDecisionRequestId;
        this.riskOutcome = riskOutcome;
        this.riskReasonCodes = riskReasonCodes;
        this.riskRequiredAssurance = riskRequiredAssurance;
        this.riskChallengeType = riskChallengeType;
        this.riskPolicyVersion = riskPolicyVersion;
        this.riskIssuedAt = riskIssuedAt;
        this.riskExpiresAt = riskExpiresAt;
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

    String customerId() { return customerId; }

    String channel() { return channel; }

    TransferDestinationClass destinationClass() { return destinationClass; }

    String correlationId() { return correlationId; }

    String transferRequestId() { return transferRequestId; }

    String reservationRequestId() { return reservationRequestId; }

    String postingRequestId() { return postingRequestId; }

    UUID riskDecisionId() { return riskDecisionId; }

    String riskDecisionRequestId() { return riskDecisionRequestId; }

    TransferRiskOutcome riskOutcome() { return riskOutcome; }

    String riskReasonCodes() { return riskReasonCodes; }

    String riskRequiredAssurance() { return riskRequiredAssurance; }

    String riskChallengeType() { return riskChallengeType; }

    String riskPolicyVersion() { return riskPolicyVersion; }

    Instant riskIssuedAt() { return riskIssuedAt; }

    Instant riskExpiresAt() { return riskExpiresAt; }

    String reservationId() { return reservationId; }

    TransferStatus status() { return status; }

    Long version() { return version; }

    void updateFrom(com.digitalbank.transactionservice.domain.Transfer transfer, Instant now) {
        this.sourceAccountId = transfer.sourceAccountId();
        this.destinationAccountId = transfer.destinationAccountId();
        this.amount = transfer.amount();
        this.currency = transfer.currency();
        this.customerId = transfer.customerId();
        this.channel = transfer.channel();
        this.destinationClass = transfer.destinationClass();
        this.correlationId = transfer.correlationId();
        this.transferRequestId = transfer.transferRequestId();
        this.reservationRequestId = transfer.reservationRequestId();
        this.postingRequestId = transfer.postingRequestId();
        var decision = transfer.riskDecision();
        this.riskDecisionId = decision == null ? null : decision.decisionId();
        this.riskDecisionRequestId = decision == null ? transfer.transferRequestId() : decision.decisionRequestId();
        this.riskOutcome = decision == null ? TransferRiskOutcome.ALLOW : decision.outcome();
        this.riskReasonCodes = decision == null ? null : String.join(",", decision.reasonCodes());
        this.riskRequiredAssurance = decision == null ? null : decision.requiredAssurance();
        this.riskChallengeType = decision == null ? null : decision.challengeType();
        this.riskPolicyVersion = decision == null ? "legacy" : decision.policyVersion();
        this.riskIssuedAt = decision == null ? null : decision.issuedAt();
        this.riskExpiresAt = decision == null ? null : decision.expiresAt();
        this.reservationId = transfer.reservationId();
        this.status = transfer.status();
        this.updatedAt = now;
    }
}
