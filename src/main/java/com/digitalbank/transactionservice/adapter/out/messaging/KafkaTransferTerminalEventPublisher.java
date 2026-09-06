package com.digitalbank.transactionservice.adapter.out.messaging;

import com.digitalbank.transactionservice.application.port.out.TransferCompletedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferFailedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.transfer-terminal.enabled", havingValue = "true")
class KafkaTransferTerminalEventPublisher {

    private static final int BATCH_SIZE = 100;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransferTerminalEventOutbox outbox;
    private final ObjectMapper objectMapper;
    private final String completedTopic;
    private final String failedTopic;
    private final long sendTimeoutSeconds;
    private final long leaseSeconds;
    private final long retryDelayMs;

    KafkaTransferTerminalEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            TransferTerminalEventOutbox outbox,
            ObjectMapper objectMapper,
            Environment environment) {
        this.kafkaTemplate = kafkaTemplate;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.completedTopic = environment.getRequiredProperty("transaction.events.transfer-terminal.completed-topic");
        this.failedTopic = environment.getRequiredProperty("transaction.events.transfer-terminal.failed-topic");
        this.sendTimeoutSeconds = environment.getProperty(
                "transaction.events.transfer-terminal.publisher.send-timeout-seconds", Long.class, 10L);
        this.leaseSeconds = environment.getProperty(
                "transaction.events.transfer-terminal.publisher.lease-seconds", Long.class, 900L);
        this.retryDelayMs = environment.getProperty(
                "transaction.events.transfer-terminal.publisher.retry-delay-ms", Long.class, 5000L);
    }

    @Scheduled(fixedDelayString = "${transaction.events.transfer-terminal.publisher.fixed-delay-ms:1000}")
    void publishReadyEvents() {
        var now = Instant.now();
        var claimToken = UUID.randomUUID();
        for (var event : outbox.claimReady(BATCH_SIZE, now, claimToken, now.plusSeconds(leaseSeconds))) {
            try {
                kafkaTemplate.send(record(event)).get(sendTimeoutSeconds, TimeUnit.SECONDS);
                outbox.markPublished(event, claimToken, Instant.now());
            } catch (Exception exception) {
                var message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                outbox.markFailed(
                        event,
                        claimToken,
                        message.substring(0, Math.min(message.length(), 2000)),
                        Instant.now().plusMillis(retryDelayMs));
            }
        }
    }

    private ProducerRecord<String, String> record(TransferTerminalEvent event) {
        var record = new ProducerRecord<>(topicFor(event), event.transactionId().toString(), serialize(event));
        addHeader(record, "event-id", event.eventId().toString());
        addHeader(record, "correlation-id", event.correlationId());
        addHeader(record, "causation-id", event.causationId());
        addHeader(record, "producer", event.producer());
        addHeader(record, "schema-version", event.schemaVersion());
        addHeader(record, "occurred-at", event.occurredAt().toString());
        return record;
    }

    private String topicFor(TransferTerminalEvent event) {
        return switch (event.eventType()) {
            case TransferCompletedEvent.EVENT_TYPE -> completedTopic;
            case TransferFailedEvent.EVENT_TYPE -> failedTopic;
            default -> throw new IllegalArgumentException("Unsupported terminal transfer event: " + event.eventType());
        };
    }

    private String serialize(TransferTerminalEvent event) {
        try {
            return objectMapper.copy()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize terminal transfer event", exception);
        }
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
