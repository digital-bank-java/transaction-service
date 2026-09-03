package com.digitalbank.transactionservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTransferWorkflowRepository extends JpaRepository<TransferWorkflowJpaEntity, UUID> {

    java.util.Optional<TransferWorkflowJpaEntity> findByCorrelationIdOrTransferRequestIdOrReservationRequestIdOrPostingRequestId(
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId);

    @Modifying
    @Query(value = """
            insert into transfer_workflows (
                id, source_account_id, destination_account_id, amount, currency,
                correlation_id, transfer_request_id, reservation_request_id,
                posting_request_id, reservation_id, status, version, created_at, updated_at
            ) values (
                :id, :sourceAccountId, :destinationAccountId, :amount, :currency,
                :correlationId, :transferRequestId, :reservationRequestId,
                :postingRequestId, :reservationId, :status, :version,
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
            @Param("correlationId") String correlationId,
            @Param("transferRequestId") String transferRequestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId,
            @Param("reservationId") String reservationId,
            @Param("status") String status,
            @Param("version") long version);
}
