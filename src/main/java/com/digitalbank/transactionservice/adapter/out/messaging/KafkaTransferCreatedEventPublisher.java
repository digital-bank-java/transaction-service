package com.digitalbank.transactionservice.adapter.out.messaging;

import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.transfer-created.enabled", havingValue = "true")
class KafkaTransferCreatedEventPublisher {

    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 10;
    private static final long LEASE_SECONDS = 900;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransferCreatedEventOutbox outbox;
    private final ObjectMapper objectMapper;
    private final String topic;

    KafkaTransferCreatedEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            TransferCreatedEventOutbox outbox,
            ObjectMapper objectMapper,
            org.springframework.core.env.Environment environment) {
        this.kafkaTemplate = kafkaTemplate;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.topic = environment.getRequiredProperty("transaction.events.transfer-created.topic");
    }

    @Scheduled(fixedDelayString = "${transaction.events.publisher.fixed-delay-ms:1000}")
    void publishReadyEvents() {
        var now = Instant.now();
        var claimToken = UUID.randomUUID();
        for (var event : outbox.claimReady(BATCH_SIZE, now, claimToken, now.plusSeconds(LEASE_SECONDS))) {
            try {
                kafkaTemplate.send(record(event)).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                outbox.markPublished(event, claimToken, Instant.now());
            } catch (Exception exception) {
                var message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                outbox.markFailed(
                        event,
                        claimToken,
                        message.substring(0, Math.min(message.length(), 2000)),
                        Instant.now().plusSeconds(5));
            }
        }
    }

    private ProducerRecord<String, String> record(TransferCreatedEvent event) {
        var record = new ProducerRecord<>(topic, event.aggregateId().toString(), serialize(event));
        addHeader(record, "event-id", event.eventId().toString());
        addHeader(record, "correlation-id", event.correlationId());
        addHeader(record, "causation-id", event.causationId());
        addHeader(record, "producer", event.producer());
        addHeader(record, "schema-version", event.schemaVersion());
        addHeader(record, "occurred-at", event.occurredAt().toString());
        return record;
    }

    private String serialize(TransferCreatedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize TransferCreated event", exception);
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
