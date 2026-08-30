package com.digitalbank.transactionservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTransferWorkflowRepository extends JpaRepository<TransferWorkflowJpaEntity, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into transfer_workflows (
                id, source_account_id, destination_account_id, amount, currency,
                correlation_id, transfer_request_id, reservation_request_id, posting_request_id,
                status, version, created_at, updated_at
            ) values (
                :id, :sourceAccountId, :destinationAccountId, :amount, :currency,
                :correlationId, :transferRequestId, :reservationRequestId, :postingRequestId,
                'PENDING', 0, now(), now()
            ) on conflict (id) do nothing
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("id") UUID id,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("correlationId") String correlationId,
            @Param("transferRequestId") String transferRequestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            merge into transfer_workflows (
                id, source_account_id, destination_account_id, amount, currency,
                correlation_id, transfer_request_id, reservation_request_id, posting_request_id,
                status, version, created_at, updated_at
            ) key (id)
            select :id, :sourceAccountId, :destinationAccountId, :amount, :currency,
                   :correlationId, :transferRequestId, :reservationRequestId, :postingRequestId,
                   'PENDING', 0, now(), now()
            where not exists (select 1 from transfer_workflows where id = :id)
            """, nativeQuery = true)
    int createIfAbsentH2(
            @Param("id") UUID id,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("correlationId") String correlationId,
            @Param("transferRequestId") String transferRequestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId);
}
