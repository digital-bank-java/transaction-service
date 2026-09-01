package com.digitalbank.transactionservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.LedgerPostingRequestedEvent;
import com.digitalbank.transactionservice.domain.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

class KafkaLedgerPostingCommandPublisherTest {

    @Test
    void publishesPostingCommandToRequestedTopicWithGovernedHeaders() {
        var event = requestedEvent();
        var kafkaTemplate = new RecordingKafkaTemplate(false);
        var outbox = new RecordingOutbox(event, "{\"persisted\":true}");

        publisher(kafkaTemplate, outbox).publishReadyEvents();

        assertThat(kafkaTemplate.records).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("ledger.posting.requested.v1");
            assertThat(record.key()).isEqualTo(event.postingRequestId());
            assertHeaders(record, event);
            assertThat(record.value()).isEqualTo("{\"persisted\":true}");
        });
        assertThat(outbox.published).containsExactly(event.eventId());
        assertThat(outbox.failed).isEmpty();
    }

    @Test
    void retriesWhenLedgerCommandPublishFails() {
        var event = requestedEvent();
        var kafkaTemplate = new RecordingKafkaTemplate(true);
        var outbox = new RecordingOutbox(event);

        publisher(kafkaTemplate, outbox).publishReadyEvents();

        assertThat(kafkaTemplate.records).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("ledger.posting.requested.v1");
            assertThat(record.key()).isEqualTo(event.postingRequestId());
            assertHeaders(record, event);
        });
        assertThat(outbox.published).isEmpty();
        assertThat(outbox.failed).containsExactly(event.eventId());
        assertThat(outbox.failureMessage).contains("broker unavailable");
    }

    @Test
    void serializesApprovedLedgerCommandContractWhenOutboxPayloadMissing() {
        var event = requestedEvent();
        var kafkaTemplate = new RecordingKafkaTemplate(false);
        var outbox = new RecordingOutbox(event);
        var objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        publisher(kafkaTemplate, outbox).publishReadyEvents();

        assertThat(kafkaTemplate.records).singleElement().satisfies(record -> {
            assertThat(objectMapper.readTree(record.value()))
                    .isEqualTo(objectMapper.readTree(expectedJson(event)));
        });
    }

    private static KafkaLedgerPostingCommandPublisher publisher(
            KafkaTemplate<String, String> kafkaTemplate, LedgerCommandEventOutbox outbox) {
        var environment = new MockEnvironment()
                .withProperty("transaction.events.ledger.requested-topic", "ledger.posting.requested.v1");
        return new KafkaLedgerPostingCommandPublisher(
                kafkaTemplate, outbox, new ObjectMapper().registerModule(new JavaTimeModule()), environment);
    }

    private static void assertHeaders(ProducerRecord<String, String> record, LedgerCommandEvent event) {
        assertThat(header(record, "event-id")).isEqualTo(event.eventId().toString());
        assertThat(header(record, "correlation-id")).isEqualTo(event.correlationId());
        assertThat(header(record, "causation-id")).isEqualTo(event.causationId());
        assertThat(header(record, "producer")).isEqualTo(event.producer());
        assertThat(header(record, "schema-version")).isEqualTo(event.schemaVersion());
        assertThat(header(record, "occurred-at")).isEqualTo(event.occurredAt().toString());
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static LedgerPostingRequestedEvent requestedEvent() {
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
        return LedgerPostingRequestedEvent.from(transfer);
    }

    private static String expectedJson(LedgerPostingRequestedEvent event) {
        return ("{\"eventId\":\"%s\",\"eventType\":\"LedgerPostingRequested.v1\",\"schemaVersion\":\"1.0.0\","
                + "\"producer\":\"transaction-service\",\"occurredAt\":\"%s\",\"aggregateId\":\"posting-request-001\","
                + "\"correlationId\":\"transfer-correlation-001\",\"causationId\":\"reservation-request-001\","
                + "\"transactionId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"reservationRequestId\":\"reservation-request-001\",\"reservationId\":\"reservation-001\","
                + "\"postingRequestId\":\"posting-request-001\","
                + "\"description\":\"Transfer posting for transfer-request-001\",\"currency\":\"AED\","
                + "\"effectiveAt\":\"%s\",\"debitLines\":[{\"accountId\":\"22222222-2222-2222-2222-222222222222\","
                + "\"amount\":\"12.50\"}],\"creditLines\":[{\"accountId\":\"33333333-3333-3333-3333-333333333333\","
                + "\"amount\":\"12.50\"}]}")
                .formatted(event.eventId(), event.occurredAt(), event.occurredAt());
    }

    private static final class RecordingKafkaTemplate extends KafkaTemplate<String, String> {
        private final boolean fail;
        private final List<ProducerRecord<String, String>> records = new ArrayList<>();

        private RecordingKafkaTemplate(boolean fail) {
            super(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "unused:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
            this.fail = fail;
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(ProducerRecord<String, String> record) {
            records.add(record);
            return fail
                    ? CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"))
                    : CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingOutbox implements LedgerCommandEventOutbox {
        private final LedgerCommandEvent event;
        private final String persistedPayload;
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> failed = new ArrayList<>();
        private String failureMessage;

        private RecordingOutbox(LedgerCommandEvent event) {
            this(event, null);
        }

        private RecordingOutbox(LedgerCommandEvent event, String persistedPayload) {
            this.event = event;
            this.persistedPayload = persistedPayload;
        }

        @Override
        public boolean recordIfAbsent(LedgerCommandEvent event) {
            return false;
        }

        @Override
        public List<LedgerCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of(event);
        }

        @Override
        public void markPublished(LedgerCommandEvent event, UUID claimToken, Instant publishedAt) {
            published.add(event.eventId());
        }

        @Override
        public void markFailed(LedgerCommandEvent event, UUID claimToken, String error, Instant retryAt) {
            failed.add(event.eventId());
            failureMessage = error;
        }

        @Override
        public Optional<String> payloadFor(LedgerCommandEvent event) {
            return Optional.ofNullable(persistedPayload);
        }
    }
}
