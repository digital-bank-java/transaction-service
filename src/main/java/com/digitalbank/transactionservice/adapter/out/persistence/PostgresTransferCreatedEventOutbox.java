package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
class PostgresTransferCreatedEventOutbox implements TransferCreatedEventOutbox {

    private final SpringDataTransferCreatedEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean h2;

    PostgresTransferCreatedEventOutbox(
            SpringDataTransferCreatedEventOutboxRepository repository,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.h2 = databaseIsH2(dataSource);
    }

    @Override
    public boolean recordIfAbsent(TransferCreatedEvent event) {
        var now = Instant.now();
        var values = eventValues(event, now);
        var recorded = h2
                ? repository.recordIfAbsentH2(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(),
                        values.occurredAt(), values.aggregateId(), values.correlationId(), values.causationId(),
                        values.transactionId(), values.sourceAccountId(), values.destinationAccountId(),
                        values.amount(), values.currency(), values.transferRequestId(), values.reservationRequestId(),
                        values.postingRequestId(), values.status(), values.availableAt(), values.jsonPayload(), values.createdAt())
                : repository.recordIfAbsent(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(),
                        values.occurredAt(), values.aggregateId(), values.correlationId(), values.causationId(),
                        values.transactionId(), values.sourceAccountId(), values.destinationAccountId(),
                        values.amount(), values.currency(), values.transferRequestId(), values.reservationRequestId(),
                        values.postingRequestId(), values.status(), values.availableAt(), values.jsonPayload(), values.createdAt());
        return recorded == 1;
    }

    @Override
    public List<TransferCreatedEvent> findReady(int limit, Instant now) {
        return repository.findTop100ByEventStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(TransferCreatedOutboxStatus.PENDING, TransferCreatedOutboxStatus.FAILED), now)
                .stream()
                .limit(limit)
                .map(this::readEvent)
                .toList();
    }

    @Override
    public void markPublished(TransferCreatedEvent event, Instant publishedAt) {
        repository.findById(event.eventId()).ifPresent(entity -> {
            entity.markPublished(publishedAt);
            repository.saveAndFlush(entity);
        });
    }

    @Override
    public void markFailed(TransferCreatedEvent event, String error, Instant retryAt) {
        repository.findById(event.eventId()).ifPresent(entity -> {
            entity.markFailed(error, retryAt);
            repository.saveAndFlush(entity);
        });
    }

    private EventValues eventValues(TransferCreatedEvent event, Instant now) {
        try {
            return new EventValues(
                    event.eventId(), event.eventType(), event.schemaVersion(), event.producer(), event.occurredAt(),
                    event.aggregateId(), event.correlationId(), event.causationId(), event.transactionId(),
                    event.sourceAccountId(), event.destinationAccountId(), new java.math.BigDecimal(event.amount()),
                    event.currency(), event.transferRequestId(), event.reservationRequestId(), event.postingRequestId(),
                    event.status().name(), now, objectMapper.writeValueAsString(event), now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize TransferCreated event", exception);
        }
    }

    private TransferCreatedEvent readEvent(TransferCreatedEventOutboxJpaEntity entity) {
        try {
            return objectMapper.readValue(entity.jsonPayload(), TransferCreatedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize TransferCreated event " + entity.eventId(), exception);
        }
    }

    private record EventValues(
            java.util.UUID eventId,
            String eventType,
            String schemaVersion,
            String producer,
            Instant occurredAt,
            java.util.UUID aggregateId,
            String correlationId,
            String causationId,
            java.util.UUID transactionId,
            java.util.UUID sourceAccountId,
            java.util.UUID destinationAccountId,
            java.math.BigDecimal amount,
            String currency,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId,
            String status,
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
