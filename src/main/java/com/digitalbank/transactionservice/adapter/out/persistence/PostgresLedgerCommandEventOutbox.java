package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.LedgerPostingRequestedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresLedgerCommandEventOutbox implements LedgerCommandEventOutbox {

    private final SpringDataLedgerCommandEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean h2;

    PostgresLedgerCommandEventOutbox(
            SpringDataLedgerCommandEventOutboxRepository repository,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.h2 = databaseIsH2(dataSource);
    }

    @Override
    @Transactional
    public boolean recordIfAbsent(LedgerCommandEvent event) {
        var now = Instant.now();
        var payload = serialize(event);
        var values = values(event, payload, now);
        var recorded = h2
                ? repository.recordIfAbsentH2(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(), values.occurredAt(),
                        values.aggregateId(), values.correlationId(), values.causationId(), values.transactionId(),
                        values.reservationRequestId(), values.reservationId(), values.postingRequestId(),
                        values.sourceAccountId(), values.destinationAccountId(), values.amount(), values.currency(),
                        values.availableAt(), values.jsonPayload(), values.createdAt())
                : repository.recordIfAbsent(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(), values.occurredAt(),
                        values.aggregateId(), values.correlationId(), values.causationId(), values.transactionId(),
                        values.reservationRequestId(), values.reservationId(), values.postingRequestId(),
                        values.sourceAccountId(), values.destinationAccountId(), values.amount(), values.currency(),
                        values.availableAt(), values.jsonPayload(), values.createdAt());
        return recorded == 1;
    }

    @Override
    @Transactional
    public List<LedgerCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
        var statuses = List.of(LedgerCommandOutboxStatus.PENDING, LedgerCommandOutboxStatus.FAILED);
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
    public void markPublished(LedgerCommandEvent event, UUID claimToken, Instant publishedAt) {
        var statuses = List.of(LedgerCommandOutboxStatus.PENDING, LedgerCommandOutboxStatus.FAILED);
        if (h2) {
            repository.markPublishedH2(event.eventId(), statuses, claimToken, publishedAt);
        } else {
            repository.markPublishedPostgres(event.eventId(), claimToken, publishedAt);
        }
    }

    @Override
    @Transactional
    public void markFailed(LedgerCommandEvent event, UUID claimToken, String error, Instant retryAt) {
        var statuses = List.of(LedgerCommandOutboxStatus.PENDING, LedgerCommandOutboxStatus.FAILED);
        if (h2) {
            repository.markFailedH2(event.eventId(), statuses, claimToken, error, retryAt);
        } else {
            repository.markFailedPostgres(event.eventId(), claimToken, error, retryAt);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> payloadFor(LedgerCommandEvent event) {
        return repository.findById(event.eventId()).map(LedgerCommandEventOutboxJpaEntity::jsonPayload);
    }

    private String serialize(LedgerCommandEvent event) {
        try {
            return objectMapper.copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize ledger command event", exception);
        }
    }

    private LedgerCommandEvent readEvent(LedgerCommandEventOutboxJpaEntity entity) {
        try {
            return switch (entity.eventType()) {
                case LedgerPostingRequestedEvent.EVENT_TYPE ->
                        objectMapper.readValue(entity.jsonPayload(), LedgerPostingRequestedEvent.class);
                default -> throw new IllegalStateException("Unsupported ledger event type: " + entity.eventType());
            };
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize ledger event " + entity.eventId(), exception);
        }
    }

    private static EventValues values(LedgerCommandEvent event, String payload, Instant now) {
        if (event instanceof LedgerPostingRequestedEvent requested) {
            return new EventValues(
                    event.eventId(),
                    event.eventType(),
                    event.schemaVersion(),
                    event.producer(),
                    event.occurredAt(),
                    event.aggregateId(),
                    event.correlationId(),
                    event.causationId(),
                    event.transactionId(),
                    event.reservationRequestId(),
                    event.reservationId(),
                    event.postingRequestId(),
                    requested.sourceAccountId(),
                    requested.destinationAccountId(),
                    requested.amountValue(),
                    requested.currency(),
                    now,
                    payload,
                    now);
        }
        throw new IllegalArgumentException("Unsupported ledger event: " + event.getClass());
    }

    private record EventValues(
            UUID eventId,
            String eventType,
            String schemaVersion,
            String producer,
            Instant occurredAt,
            String aggregateId,
            String correlationId,
            String causationId,
            UUID transactionId,
            String reservationRequestId,
            String reservationId,
            String postingRequestId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            java.math.BigDecimal amount,
            String currency,
            Instant availableAt,
            String jsonPayload,
            Instant createdAt) {}

    private static boolean databaseIsH2(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not determine database product", exception);
        }
    }
}
