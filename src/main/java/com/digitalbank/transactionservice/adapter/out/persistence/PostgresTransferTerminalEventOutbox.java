package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.TransferCompletedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferFailedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class PostgresTransferTerminalEventOutbox implements TransferTerminalEventOutbox {

    private final SpringDataTransferTerminalEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean h2;

    PostgresTransferTerminalEventOutbox(
            SpringDataTransferTerminalEventOutboxRepository repository,
            ObjectMapper objectMapper,
            DataSource dataSource) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.h2 = databaseIsH2(dataSource);
    }

    @Override
    @Transactional
    public boolean recordIfAbsent(TransferTerminalEvent event) {
        var now = Instant.now();
        var values = eventValues(event, now);
        var recorded = h2
                ? repository.recordIfAbsentH2(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(),
                        values.occurredAt(), values.aggregateId(), values.correlationId(), values.causationId(),
                        values.transactionId(), values.sourceAccountId(), values.destinationAccountId(), values.amount(),
                        values.currency(), values.transferRequestId(), values.reservationRequestId(), values.reservationId(),
                        values.postingRequestId(), values.postingId(), values.status(), values.failureStage(),
                        values.failureCode(), values.failureReason(), values.compensationStatus(),
                        values.manualReviewRequired(), values.availableAt(), values.jsonPayload(), values.createdAt())
                : repository.recordIfAbsent(
                        values.eventId(), values.eventType(), values.schemaVersion(), values.producer(),
                        values.occurredAt(), values.aggregateId(), values.correlationId(), values.causationId(),
                        values.transactionId(), values.sourceAccountId(), values.destinationAccountId(), values.amount(),
                        values.currency(), values.transferRequestId(), values.reservationRequestId(), values.reservationId(),
                        values.postingRequestId(), values.postingId(), values.status(), values.failureStage(),
                        values.failureCode(), values.failureReason(), values.compensationStatus(),
                        values.manualReviewRequired(), values.availableAt(), values.jsonPayload(), values.createdAt());
        return recorded == 1;
    }

    @Override
    @Transactional
    public List<TransferTerminalEvent> claimReady(
            int limit, Instant now, UUID claimToken, Instant leaseUntil) {
        if (h2) {
            var statuses = List.of(TransferTerminalOutboxStatus.PENDING, TransferTerminalOutboxStatus.FAILED);
            repository.findTop100ByEventStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(statuses, now)
                    .stream()
                    .limit(limit)
                    .forEach(entity -> repository.claimReadyH2(
                            entity.eventId(), statuses, now, claimToken, leaseUntil));
        } else {
            repository.claimReadyPostgres(limit, now, claimToken, leaseUntil);
        }
        return repository.findByProcessingTokenOrderByCreatedAtAsc(claimToken)
                .stream()
                .map(this::readEvent)
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(TransferTerminalEvent event, UUID claimToken, Instant publishedAt) {
        var statuses = List.of(TransferTerminalOutboxStatus.PENDING, TransferTerminalOutboxStatus.FAILED);
        if (h2) {
            repository.markPublishedH2(event.eventId(), claimToken, statuses, publishedAt);
        } else {
            repository.markPublishedPostgres(event.eventId(), claimToken, publishedAt);
        }
    }

    @Override
    @Transactional
    public void markFailed(TransferTerminalEvent event, UUID claimToken, String error, Instant retryAt) {
        if (h2) {
            var statuses = List.of(TransferTerminalOutboxStatus.PENDING, TransferTerminalOutboxStatus.FAILED);
            repository.markFailedH2(event.eventId(), claimToken, statuses, error, retryAt);
        } else {
            repository.markFailedPostgres(event.eventId(), claimToken, error, retryAt);
        }
    }

    private EventValues eventValues(TransferTerminalEvent event, Instant now) {
        try {
            return new EventValues(
                    event.eventId(), event.eventType(), event.schemaVersion(), event.producer(), event.occurredAt(),
                    event.aggregateId(), event.correlationId(), event.causationId(), event.transactionId(),
                    event.sourceAccountId(), event.destinationAccountId(), new java.math.BigDecimal(event.amount()), event.currency(),
                    event.transferRequestId(), event.reservationRequestId(), event.reservationId(),
                    event.postingRequestId(), event.postingId(), event.status(), event.failureStage(), event.failureCode(),
                    event.failureReason(), event.compensationStatus(), event.manualReviewRequired(), now,
                    objectMapper.copy()
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                            .writeValueAsString(event), now);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize terminal transfer event", exception);
        }
    }

    private TransferTerminalEvent readEvent(TransferTerminalEventOutboxJpaEntity entity) {
        try {
            JsonNode payload = objectMapper.readTree(entity.jsonPayload());
            return switch (payload.path("eventType").asText()) {
                case TransferCompletedEvent.EVENT_TYPE -> objectMapper.treeToValue(payload, TransferCompletedEvent.class);
                case TransferFailedEvent.EVENT_TYPE -> objectMapper.treeToValue(payload, TransferFailedEvent.class);
                default -> throw new IllegalStateException("Unsupported terminal transfer event: " + entity.eventType());
            };
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not deserialize terminal transfer event " + entity.eventId(), exception);
        }
    }

    private record EventValues(
            UUID eventId,
            String eventType,
            String schemaVersion,
            String producer,
            Instant occurredAt,
            UUID aggregateId,
            String correlationId,
            String causationId,
            UUID transactionId,
            UUID sourceAccountId,
            UUID destinationAccountId,
            java.math.BigDecimal amount,
            String currency,
            String transferRequestId,
            String reservationRequestId,
            String reservationId,
            String postingRequestId,
            String postingId,
            String status,
            String failureStage,
            String failureCode,
            String failureReason,
            String compensationStatus,
            Boolean manualReviewRequired,
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
