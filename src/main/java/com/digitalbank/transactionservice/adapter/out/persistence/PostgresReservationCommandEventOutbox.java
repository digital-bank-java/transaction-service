package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.AccountReservationReleaseRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.AccountReservationRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresReservationCommandEventOutbox implements ReservationCommandEventOutbox {

    private final SpringDataReservationCommandEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean h2;

    PostgresReservationCommandEventOutbox(
            SpringDataReservationCommandEventOutboxRepository repository,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.h2 = databaseIsH2(dataSource);
    }

    @Override
    @Transactional
    public boolean recordIfAbsent(ReservationCommandEvent event) {
        var now = Instant.now();
        var payload = serialize(event);
        var values = values(event, payload, now);
        var recorded = h2
                ? repository.recordIfAbsentH2(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(), values.occurredAt(),
                        values.aggregateId(), values.correlationId(), values.causationId(), values.transactionId(),
                        values.reservationRequestId(), values.sourceAccountId(), values.destinationAccountId(), values.amount(),
                        values.currency(), values.reservationId(), values.postingRequestId(), values.expiresAt(), values.reason(),
                        values.availableAt(), values.jsonPayload(), values.createdAt())
                : repository.recordIfAbsent(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(), values.occurredAt(),
                        values.aggregateId(), values.correlationId(), values.causationId(), values.transactionId(),
                        values.reservationRequestId(), values.sourceAccountId(), values.destinationAccountId(), values.amount(),
                        values.currency(), values.reservationId(), values.postingRequestId(), values.expiresAt(), values.reason(),
                        values.availableAt(), values.jsonPayload(), values.createdAt());
        return recorded == 1;
    }

    @Override
    @Transactional
    public List<ReservationCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
        var statuses = List.of(ReservationCommandOutboxStatus.PENDING, ReservationCommandOutboxStatus.FAILED);
        if (h2) {
            repository.findTop100ByEventStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(statuses, now)
                    .stream()
                    .limit(limit)
                    .forEach(entity -> repository.claimReadyH2(entity.eventId(), statuses, now, claimToken, leaseUntil));
        } else {
            repository.claimReadyPostgres(limit, now, claimToken, leaseUntil);
        }
        return repository.findByProcessingTokenOrderByCreatedAtAsc(claimToken).stream().map(this::readEvent).toList();
    }

    @Override
    @Transactional
    public void markPublished(ReservationCommandEvent event, UUID claimToken, Instant publishedAt) {
        var statuses = List.of(ReservationCommandOutboxStatus.PENDING, ReservationCommandOutboxStatus.FAILED);
        if (h2) {
            repository.markPublishedH2(event.eventId(), statuses, claimToken, publishedAt);
        } else {
            repository.markPublishedPostgres(event.eventId(), claimToken, publishedAt);
        }
    }

    @Override
    @Transactional
    public void markFailed(ReservationCommandEvent event, UUID claimToken, String error, Instant retryAt) {
        var statuses = List.of(ReservationCommandOutboxStatus.PENDING, ReservationCommandOutboxStatus.FAILED);
        if (h2) {
            repository.markFailedH2(event.eventId(), statuses, claimToken, error, retryAt);
        } else {
            repository.markFailedPostgres(event.eventId(), claimToken, error, retryAt);
        }
    }

    private String serialize(ReservationCommandEvent event) {
        try {
            return objectMapper.copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize reservation command event", exception);
        }
    }

    private ReservationCommandEvent readEvent(ReservationCommandEventOutboxJpaEntity entity) {
        try {
            return switch (entity.eventType()) {
                case AccountReservationRequestedEvent.EVENT_TYPE ->
                        objectMapper.readValue(entity.jsonPayload(), AccountReservationRequestedEvent.class);
                case AccountReservationReleaseRequestedEvent.EVENT_TYPE ->
                        objectMapper.readValue(entity.jsonPayload(), AccountReservationReleaseRequestedEvent.class);
                default -> throw new IllegalStateException("Unsupported reservation event type: " + entity.eventType());
            };
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize reservation event " + entity.eventId(), exception);
        }
    }

    private static EventValues values(ReservationCommandEvent event, String payload, Instant now) {
        var values = new EventValues(
                event.eventId(), event.eventType(), event.schemaVersion(), event.producer(), event.occurredAt(),
                event.aggregateId(), event.correlationId(), event.causationId(), event.transactionId(),
                event.reservationRequestId(), event.sourceAccountId(), null, null, null, null, null, null, null,
                now, payload, now);
        if (event instanceof AccountReservationRequestedEvent requested) {
            return values.with(requested.destinationAccountId(), requested.amountValue(), requested.currency(), null, null,
                    requested.expiresAt(), null);
        }
        if (event instanceof AccountReservationReleaseRequestedEvent release) {
            return values.with(null, null, null, release.reservationId(), release.postingRequestId(), null, release.reason());
        }
        throw new IllegalArgumentException("Unsupported reservation event: " + event.getClass());
    }

    private record EventValues(
            UUID eventId, String eventType, String schemaVersion, String producer, Instant occurredAt,
            String aggregateId, String correlationId, String causationId, UUID transactionId,
            String reservationRequestId, UUID sourceAccountId, UUID destinationAccountId, java.math.BigDecimal amount,
            String currency, String reservationId, String postingRequestId, Instant expiresAt, String reason,
            Instant availableAt, String jsonPayload, Instant createdAt) {
        EventValues with(UUID destination, java.math.BigDecimal eventAmount, String eventCurrency,
                String eventReservationId, String eventPostingRequestId, Instant eventExpiresAt, String eventReason) {
            return new EventValues(eventId, eventType, schemaVersion, producer, occurredAt, aggregateId, correlationId,
                    causationId, transactionId, reservationRequestId, sourceAccountId, destination, eventAmount,
                    eventCurrency, eventReservationId, eventPostingRequestId, eventExpiresAt, eventReason,
                    availableAt, jsonPayload, createdAt);
        }
    }

    private static boolean databaseIsH2(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not determine database product", exception);
        }
    }
}
