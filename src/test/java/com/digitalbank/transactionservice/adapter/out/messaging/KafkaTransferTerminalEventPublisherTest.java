package com.digitalbank.transactionservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.application.port.out.TransferCompletedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEventOutbox;
import com.digitalbank.transactionservice.domain.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.mock.env.MockEnvironment;

class KafkaTransferTerminalEventPublisherTest {

    @Test
    void publishesCompletedTransferWithGovernedTopicKeyHeadersAndPayload() throws Exception {
        var event = completedEvent();
        var kafkaTemplate = new RecordingKafkaTemplate();
        var publisher = new KafkaTransferTerminalEventPublisher(
                kafkaTemplate,
                new RecordingOutbox(event),
                new ObjectMapper().registerModule(new JavaTimeModule()),
                new MockEnvironment()
                        .withProperty("transaction.events.transfer-terminal.completed-topic", "events.transfer.completed.v1")
                        .withProperty("transaction.events.transfer-terminal.failed-topic", "events.transfer.failed.v1"));

        publisher.publishReadyEvents();

        assertThat(kafkaTemplate.records).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("events.transfer.completed.v1");
            assertThat(record.key()).isEqualTo(event.transactionId().toString());
            assertThat(header(record, "event-id")).isEqualTo(event.eventId().toString());
            assertThat(header(record, "correlation-id")).isEqualTo(event.correlationId());
            assertThat(header(record, "causation-id")).isEqualTo(event.causationId());
            assertThat(header(record, "producer")).isEqualTo(event.producer());
            assertThat(header(record, "schema-version")).isEqualTo(event.schemaVersion());
            assertThat(header(record, "occurred-at")).isEqualTo(event.occurredAt().toString());
            assertThat(new ObjectMapper().readTree(record.value()).get("amount").asText()).isEqualTo("12.50");
            assertThat(new ObjectMapper().readTree(record.value()).get("occurredAt").asText())
                    .isEqualTo("2026-09-07T00:00:00Z");
        });
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static TransferCompletedEvent completedEvent() {
        var transfer = Transfer.request(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                new BigDecimal("12.50"),
                "AED",
                "transfer-correlation-001",
                "transfer-request-001",
                "reservation-request-001",
                "posting-request-001");
        transfer.accountReservationCreated("reservation-request-001", "reservation-001", "transfer-correlation-001");
        transfer.ledgerPostingCompleted("posting-request-001", "transfer-correlation-001");
        return TransferCompletedEvent.from(
                transfer, "ledger-event-001", "posting-001", Instant.parse("2026-09-07T00:00:00Z"));
    }

    private static final class RecordingKafkaTemplate extends KafkaTemplate<String, String> {
        private final List<ProducerRecord<String, String>> records = new ArrayList<>();

        private RecordingKafkaTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
            records.add(record);
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingOutbox implements TransferTerminalEventOutbox {
        private final TransferTerminalEvent event;

        private RecordingOutbox(TransferTerminalEvent event) {
            this.event = event;
        }

        @Override
        public boolean recordIfAbsent(TransferTerminalEvent event) {
            return false;
        }

        @Override
        public List<TransferTerminalEvent> claimReady(
                int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of(event);
        }

        @Override
        public void markPublished(TransferTerminalEvent event, UUID claimToken, Instant publishedAt) {}

        @Override
        public void markFailed(TransferTerminalEvent event, UUID claimToken, String error, Instant retryAt) {}
    }
}
