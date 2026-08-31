package com.digitalbank.transactionservice.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataTransferCreatedEventOutboxRepository
        extends JpaRepository<TransferCreatedEventOutboxJpaEntity, UUID> {

    List<TransferCreatedEventOutboxJpaEntity> findTop100ByEventStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            List<TransferCreatedOutboxStatus> statuses, Instant now);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into transfer_created_event_outbox (
                event_id, event_type, schema_version, producer, occurred_at,
                aggregate_id, correlation_id, causation_id, transaction_id,
                source_account_id, destination_account_id, amount, currency,
                transfer_request_id, reservation_request_id, posting_request_id,
                status, event_status, attempt_count, available_at, json_payload, created_at
            ) values (
                :eventId, :eventType, :schemaVersion, :producer, :occurredAt,
                :aggregateId, :correlationId, :causationId, :transactionId,
                :sourceAccountId, :destinationAccountId, :amount, :currency,
                :transferRequestId, :reservationRequestId, :postingRequestId,
                :status, 'PENDING', 0, :availableAt, :jsonPayload, :createdAt
            ) on conflict (aggregate_id, event_type) do nothing
            """, nativeQuery = true)
    int recordIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("schemaVersion") String schemaVersion,
            @Param("producer") String producer,
            @Param("occurredAt") Instant occurredAt,
            @Param("aggregateId") UUID aggregateId,
            @Param("correlationId") String correlationId,
            @Param("causationId") String causationId,
            @Param("transactionId") UUID transactionId,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("transferRequestId") String transferRequestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId,
            @Param("status") String status,
            @Param("availableAt") Instant availableAt,
            @Param("jsonPayload") String jsonPayload,
            @Param("createdAt") Instant createdAt);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            merge into transfer_created_event_outbox (
                event_id, event_type, schema_version, producer, occurred_at,
                aggregate_id, correlation_id, causation_id, transaction_id,
                source_account_id, destination_account_id, amount, currency,
                transfer_request_id, reservation_request_id, posting_request_id,
                status, event_status, attempt_count, available_at, json_payload, created_at
            ) key (aggregate_id, event_type)
            select :eventId, :eventType, :schemaVersion, :producer, :occurredAt,
                   :aggregateId, :correlationId, :causationId, :transactionId,
                   :sourceAccountId, :destinationAccountId, :amount, :currency,
                   :transferRequestId, :reservationRequestId, :postingRequestId,
                   :status, 'PENDING', 0, :availableAt, :jsonPayload, :createdAt
            where not exists (
                select 1 from transfer_created_event_outbox
                where aggregate_id = :aggregateId and event_type = :eventType
            )
            """, nativeQuery = true)
    int recordIfAbsentH2(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("schemaVersion") String schemaVersion,
            @Param("producer") String producer,
            @Param("occurredAt") Instant occurredAt,
            @Param("aggregateId") UUID aggregateId,
            @Param("correlationId") String correlationId,
            @Param("causationId") String causationId,
            @Param("transactionId") UUID transactionId,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("transferRequestId") String transferRequestId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("postingRequestId") String postingRequestId,
            @Param("status") String status,
            @Param("availableAt") Instant availableAt,
            @Param("jsonPayload") String jsonPayload,
            @Param("createdAt") Instant createdAt);
}
