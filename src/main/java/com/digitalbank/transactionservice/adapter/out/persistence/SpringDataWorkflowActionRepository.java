package com.digitalbank.transactionservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataWorkflowActionRepository extends JpaRepository<WorkflowActionJpaEntity, String> {

    long countByTransferId(UUID transferId);

    @Modifying
    @Query(value = """
            insert into transfer_workflow_actions (
                action_id, transfer_id, action_type, correlation_id,
                source_account_id, destination_account_id, amount, currency,
                request_id, reservation_request_id, posting_request_id,
                reservation_id, created_at
            ) values (
                :actionId, :transferId, :actionType, :correlationId,
                :sourceAccountId, :destinationAccountId, :amount, :currency,
                :requestId, :reservationRequestId, :postingRequestId,
                :reservationId, current_timestamp
            )
            on conflict (action_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("actionId") String actionId,
            @Param("transferId") UUID transferId,
            @Param("actionType") String actionType,
            @Param("correlationId") String correlationId,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("requestId") String requestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId,
            @Param("reservationId") String reservationId);
}
