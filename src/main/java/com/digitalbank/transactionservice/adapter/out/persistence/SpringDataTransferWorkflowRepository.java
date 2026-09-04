package com.digitalbank.transactionservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTransferWorkflowRepository extends JpaRepository<TransferWorkflowJpaEntity, UUID> {

    java.util.Optional<TransferWorkflowJpaEntity> findByCorrelationIdOrTransferRequestIdOrReservationRequestIdOrPostingRequestIdOrRiskDecisionRequestId(
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId,
            String riskDecisionRequestId);

    @Modifying
    @Query(value = """
            insert into transfer_workflows (
                id, source_account_id, destination_account_id, amount, currency,
                customer_id, channel, destination_class,
                correlation_id, transfer_request_id, reservation_request_id,
                posting_request_id, risk_decision_id, risk_decision_request_id, risk_outcome,
                risk_reason_codes, risk_required_assurance, risk_challenge_type, risk_policy_version,
                risk_issued_at, risk_expires_at, reservation_id, status, version, created_at, updated_at
            ) values (
                :id, :sourceAccountId, :destinationAccountId, :amount, :currency,
                :customerId, :channel, :destinationClass,
                :correlationId, :transferRequestId, :reservationRequestId,
                :postingRequestId, :riskDecisionId, :riskDecisionRequestId, :riskOutcome,
                :riskReasonCodes, :riskRequiredAssurance, :riskChallengeType, :riskPolicyVersion,
                :riskIssuedAt, :riskExpiresAt, :reservationId, :status, :version,
                current_timestamp, current_timestamp
            )
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("customerId") String customerId,
            @Param("channel") String channel,
            @Param("destinationClass") String destinationClass,
            @Param("correlationId") String correlationId,
            @Param("transferRequestId") String transferRequestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId,
            @Param("riskDecisionId") UUID riskDecisionId,
            @Param("riskDecisionRequestId") String riskDecisionRequestId,
            @Param("riskOutcome") String riskOutcome,
            @Param("riskReasonCodes") String riskReasonCodes,
            @Param("riskRequiredAssurance") String riskRequiredAssurance,
            @Param("riskChallengeType") String riskChallengeType,
            @Param("riskPolicyVersion") String riskPolicyVersion,
            @Param("riskIssuedAt") java.time.Instant riskIssuedAt,
            @Param("riskExpiresAt") java.time.Instant riskExpiresAt,
            @Param("reservationId") String reservationId,
            @Param("status") String status,
            @Param("version") long version);
}
