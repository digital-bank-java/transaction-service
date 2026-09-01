package com.digitalbank.transactionservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEventOutbox;
import com.digitalbank.transactionservice.domain.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;

class KafkaTransferCreatedEventPublisherTest {

    @Test
    void publishesClaimedEventAndMarksItPublished() {
        var kafkaTemplate = new RecordingKafkaTemplate(false);
        var outbox = new RecordingOutbox(event());
        var publisher = publisher(kafkaTemplate, outbox);

        publisher.publishReadyEvents();

        assertThat(outbox.published).containsExactly(outbox.event.eventId());
        assertThat(outbox.failed).isEmpty();
    }

    @Test
    void marksClaimedEventFailedWhenKafkaPublishFails() {
        var kafkaTemplate = new RecordingKafkaTemplate(true);
        var outbox = new RecordingOutbox(event());
        var publisher = publisher(kafkaTemplate, outbox);

        publisher.publishReadyEvents();

        assertThat(outbox.published).isEmpty();
        assertThat(outbox.failed).containsExactly(outbox.event.eventId());
        assertThat(outbox.failureMessage).contains("broker unavailable");
    }

    private static KafkaTransferCreatedEventPublisher publisher(
            KafkaTemplate<String, String> kafkaTemplate, TransferCreatedEventOutbox outbox) {
        var environment = new MockEnvironment().withProperty("transaction.events.transfer-created.topic", "events.transfer.created.v1");
        return new KafkaTransferCreatedEventPublisher(
                kafkaTemplate,
                outbox,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                environment);
    }

    private static TransferCreatedEvent event() {
        return TransferCreatedEvent.from(Transfer.request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("12.50"),
                "AED",
                "correlation-001",
                "transfer-request-001",
                "reservation-request-001",
                "posting-request-001"));
    }

    private static final class RecordingKafkaTemplate extends KafkaTemplate<String, String> {
        private final boolean fail;

        private RecordingKafkaTemplate(boolean fail) {
            super(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
            this.fail = fail;
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
            if (fail) {
                return CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingOutbox implements TransferCreatedEventOutbox {
        private final TransferCreatedEvent event;
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> failed = new ArrayList<>();
        private String failureMessage;

        private RecordingOutbox(TransferCreatedEvent event) {
            this.event = event;
        }

        @Override
        public boolean recordIfAbsent(TransferCreatedEvent event) {
            return false;
        }

        @Override
        public List<TransferCreatedEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of(event);
        }

        @Override
        public void markPublished(TransferCreatedEvent event, UUID claimToken, Instant publishedAt) {
            published.add(event.eventId());
        }

        @Override
        public void markFailed(TransferCreatedEvent event, UUID claimToken, String error, Instant retryAt) {
            failed.add(event.eventId());
            failureMessage = error;
        }
    }
}
