package com.digitalbank.transactionservice.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataReservationCommandEventOutboxRepository
        extends JpaRepository<ReservationCommandEventOutboxJpaEntity, UUID> {

    List<ReservationCommandEventOutboxJpaEntity> findTop100ByEventStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            List<ReservationCommandOutboxStatus> statuses, Instant now);

    List<ReservationCommandEventOutboxJpaEntity> findByProcessingTokenOrderByCreatedAtAsc(UUID processingToken);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            with claim_lock as materialized (
                select pg_try_advisory_xact_lock(
                        hashtextextended('transaction-service.reservation-command-event-outbox', 0)) as acquired
            ), candidates as (
                select outbox.event_id
                from reservation_command_event_outbox outbox
                cross join claim_lock
                where event_status in ('PENDING', 'FAILED')
                  and claim_lock.acquired
                  and available_at <= :now
                  and (processing_until is null or processing_until <= :now)
                order by outbox.created_at, outbox.event_id
                for update skip locked
                limit :limit
            )
            update reservation_command_event_outbox outbox
               set processing_token = :claimToken,
                   processing_until = :leaseUntil
             where outbox.event_id in (select event_id from candidates)
               and outbox.event_status in ('PENDING', 'FAILED')
               and outbox.available_at <= :now
               and (outbox.processing_until is null or outbox.processing_until <= :now)
            """, nativeQuery = true)
    int claimReadyPostgres(
            @Param("limit") int limit,
            @Param("now") Instant now,
            @Param("claimToken") UUID claimToken,
            @Param("leaseUntil") Instant leaseUntil);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ReservationCommandEventOutboxJpaEntity outbox
               set outbox.processingToken = :claimToken,
                   outbox.processingUntil = :leaseUntil
             where outbox.eventId = :eventId
               and outbox.eventStatus in :statuses
               and outbox.availableAt <= :now
               and (outbox.processingUntil is null or outbox.processingUntil <= :now)
            """)
    int claimReadyH2(
            @Param("eventId") UUID eventId,
            @Param("statuses") List<ReservationCommandOutboxStatus> statuses,
            @Param("now") Instant now,
            @Param("claimToken") UUID claimToken,
            @Param("leaseUntil") Instant leaseUntil);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update reservation_command_event_outbox
               set event_status = 'PUBLISHED',
                   published_at = :publishedAt,
                   last_error = null,
                   processing_token = null,
                   processing_until = null
             where event_id = :eventId
               and event_status in ('PENDING', 'FAILED')
               and processing_token = :claimToken
            """, nativeQuery = true)
    int markPublishedPostgres(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("publishedAt") Instant publishedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ReservationCommandEventOutboxJpaEntity outbox
               set outbox.eventStatus = com.digitalbank.transactionservice.adapter.out.persistence.ReservationCommandOutboxStatus.PUBLISHED,
                   outbox.publishedAt = :publishedAt,
                   outbox.lastError = null,
                   outbox.processingToken = null,
                   outbox.processingUntil = null
             where outbox.eventId = :eventId
               and outbox.eventStatus in :statuses
               and outbox.processingToken = :claimToken
            """)
    int markPublishedH2(
            @Param("eventId") UUID eventId,
            @Param("statuses") List<ReservationCommandOutboxStatus> statuses,
            @Param("claimToken") UUID claimToken,
            @Param("publishedAt") Instant publishedAt);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            update reservation_command_event_outbox
               set event_status = 'FAILED',
                   attempt_count = attempt_count + 1,
                   available_at = :retryAt,
                   last_error = :error,
                   processing_token = null,
                   processing_until = null
             where event_id = :eventId
               and event_status in ('PENDING', 'FAILED')
               and processing_token = :claimToken
            """, nativeQuery = true)
    int markFailedPostgres(
            @Param("eventId") UUID eventId,
            @Param("claimToken") UUID claimToken,
            @Param("error") String error,
            @Param("retryAt") Instant retryAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ReservationCommandEventOutboxJpaEntity outbox
               set outbox.eventStatus = com.digitalbank.transactionservice.adapter.out.persistence.ReservationCommandOutboxStatus.FAILED,
                   outbox.attemptCount = outbox.attemptCount + 1,
                   outbox.availableAt = :retryAt,
                   outbox.lastError = :error,
                   outbox.processingToken = null,
                   outbox.processingUntil = null
             where outbox.eventId = :eventId
               and outbox.eventStatus in :statuses
               and outbox.processingToken = :claimToken
            """)
    int markFailedH2(
            @Param("eventId") UUID eventId,
            @Param("statuses") List<ReservationCommandOutboxStatus> statuses,
            @Param("claimToken") UUID claimToken,
            @Param("error") String error,
            @Param("retryAt") Instant retryAt);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into reservation_command_event_outbox (
                event_id, event_type, schema_version, producer, occurred_at,
                aggregate_id, correlation_id, causation_id, transaction_id,
                reservation_request_id, source_account_id, destination_account_id,
                amount, currency, reservation_id, posting_request_id, expires_at,
                reason, event_status, attempt_count, available_at, json_payload, created_at
            ) values (
                :eventId, :eventType, :schemaVersion, :producer, :occurredAt,
                :aggregateId, :correlationId, :causationId, :transactionId,
                :reservationRequestId, :sourceAccountId, :destinationAccountId,
                :amount, :currency, :reservationId, :postingRequestId, :expiresAt,
                :reason, 'PENDING', 0, :availableAt, :jsonPayload, :createdAt
            ) on conflict (aggregate_id, event_type) do nothing
            """, nativeQuery = true)
    int recordIfAbsent(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("schemaVersion") String schemaVersion,
            @Param("producer") String producer,
            @Param("occurredAt") Instant occurredAt,
            @Param("aggregateId") String aggregateId,
            @Param("correlationId") String correlationId,
            @Param("causationId") String causationId,
            @Param("transactionId") UUID transactionId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("reservationId") String reservationId,
            @Param("postingRequestId") String postingRequestId,
            @Param("expiresAt") Instant expiresAt,
            @Param("reason") String reason,
            @Param("availableAt") Instant availableAt,
            @Param("jsonPayload") String jsonPayload,
            @Param("createdAt") Instant createdAt);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            merge into reservation_command_event_outbox (
                event_id, event_type, schema_version, producer, occurred_at,
                aggregate_id, correlation_id, causation_id, transaction_id,
                reservation_request_id, source_account_id, destination_account_id,
                amount, currency, reservation_id, posting_request_id, expires_at,
                reason, event_status, attempt_count, available_at, json_payload, created_at
            ) key (aggregate_id, event_type)
            select :eventId, :eventType, :schemaVersion, :producer, :occurredAt,
                   :aggregateId, :correlationId, :causationId, :transactionId,
                   :reservationRequestId, :sourceAccountId, :destinationAccountId,
                   :amount, :currency, :reservationId, :postingRequestId, :expiresAt,
                   :reason, 'PENDING', 0, :availableAt, :jsonPayload, :createdAt
            where not exists (
                select 1 from reservation_command_event_outbox
                where aggregate_id = :aggregateId and event_type = :eventType
            )
            """, nativeQuery = true)
    int recordIfAbsentH2(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType,
            @Param("schemaVersion") String schemaVersion,
            @Param("producer") String producer,
            @Param("occurredAt") Instant occurredAt,
            @Param("aggregateId") String aggregateId,
            @Param("correlationId") String correlationId,
            @Param("causationId") String causationId,
            @Param("transactionId") UUID transactionId,
            @Param("reservationRequestId") String reservationRequestId,
            @Param("sourceAccountId") UUID sourceAccountId,
            @Param("destinationAccountId") UUID destinationAccountId,
            @Param("amount") java.math.BigDecimal amount,
            @Param("currency") String currency,
            @Param("reservationId") String reservationId,
            @Param("postingRequestId") String postingRequestId,
            @Param("expiresAt") Instant expiresAt,
            @Param("reason") String reason,
            @Param("availableAt") Instant availableAt,
            @Param("jsonPayload") String jsonPayload,
            @Param("createdAt") Instant createdAt);
}
