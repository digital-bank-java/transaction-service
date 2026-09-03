package com.digitalbank.transactionservice.adapter.out.messaging;

import com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.ledger.enabled", havingValue = "true")
class KafkaLedgerPostingCommandPublisher {

    private static final int BATCH_SIZE = 100;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LedgerCommandEventOutbox outbox;
    private final ObjectMapper objectMapper;
    private final String requestedTopic;
    private final long sendTimeoutSeconds;
    private final long leaseSeconds;
    private final long retryDelayMs;

    KafkaLedgerPostingCommandPublisher(
            @Qualifier("ledgerKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            LedgerCommandEventOutbox outbox,
            ObjectMapper objectMapper,
            Environment environment) {
        this.kafkaTemplate = kafkaTemplate;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.requestedTopic = environment.getRequiredProperty("transaction.events.ledger.requested-topic");
        this.sendTimeoutSeconds = environment.getProperty(
                "transaction.events.ledger.publisher.send-timeout-seconds", Long.class, 10L);
        this.leaseSeconds = environment.getProperty(
                "transaction.events.ledger.publisher.lease-seconds", Long.class, 900L);
        this.retryDelayMs = environment.getProperty(
                "transaction.events.ledger.publisher.retry-delay-ms", Long.class, 5000L);
    }

    @Scheduled(fixedDelayString = "${transaction.events.ledger.publisher.fixed-delay-ms:1000}")
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

    private ProducerRecord<String, String> record(LedgerCommandEvent event) {
        var record = new ProducerRecord<>(requestedTopic, event.postingRequestId(), payloadFor(event));
        addHeader(record, "event-id", event.eventId().toString());
        addHeader(record, "correlation-id", event.correlationId());
        addHeader(record, "causation-id", event.causationId());
        addHeader(record, "producer", event.producer());
        addHeader(record, "schema-version", event.schemaVersion());
        addHeader(record, "occurred-at", event.occurredAt().toString());
        return record;
    }

    private String payloadFor(LedgerCommandEvent event) {
        return outbox.payloadFor(event).orElseGet(() -> {
            try {
                return objectMapper.writeValueAsString(event);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize ledger command event", exception);
            }
        });
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }
}
