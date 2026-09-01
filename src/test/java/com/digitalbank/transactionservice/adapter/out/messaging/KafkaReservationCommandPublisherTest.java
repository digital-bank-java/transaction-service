package com.digitalbank.transactionservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.application.port.out.AccountReservationReleaseRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.AccountReservationRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEventOutbox;
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

class KafkaReservationCommandPublisherTest {

    @Test
    void publishesRequestedCommandToRequestedTopicWithGovernedHeaders() {
        var event = requestedEvent();
        var kafkaTemplate = new RecordingKafkaTemplate(false);
        var outbox = new RecordingOutbox(event, "{\"persisted\":true}");

        publisher(kafkaTemplate, outbox).publishReadyEvents();

        assertThat(kafkaTemplate.records).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("account.reservation.requested.v1");
            assertThat(record.key()).isEqualTo(event.sourceAccountId().toString());
            assertHeaders(record, event);
            assertThat(record.value()).isEqualTo("{\"persisted\":true}");
        });
        assertThat(outbox.published).containsExactly(event.eventId());
        assertThat(outbox.failed).isEmpty();
    }

    @Test
    void publishesReleaseCommandToReleaseTopicAndRetriesOnFailure() {
        var event = releaseEvent();
        var kafkaTemplate = new RecordingKafkaTemplate(true);
        var outbox = new RecordingOutbox(event);

        publisher(kafkaTemplate, outbox).publishReadyEvents();

        assertThat(kafkaTemplate.records).singleElement().satisfies(record -> {
            assertThat(record.topic()).isEqualTo("account.reservation.release-requested.v1");
            assertThat(record.key()).isEqualTo(event.sourceAccountId().toString());
            assertHeaders(record, event);
        });
        assertThat(outbox.published).isEmpty();
        assertThat(outbox.failed).containsExactly(event.eventId());
        assertThat(outbox.failureMessage).contains("broker unavailable");
    }

    private static KafkaReservationCommandPublisher publisher(
            KafkaTemplate<String, String> kafkaTemplate, ReservationCommandEventOutbox outbox) {
        var environment = new MockEnvironment()
                .withProperty("transaction.events.reservation.requested-topic", "account.reservation.requested.v1")
                .withProperty(
                        "transaction.events.reservation.release-requested-topic",
                        "account.reservation.release-requested.v1");
        return new KafkaReservationCommandPublisher(
                kafkaTemplate, outbox, new ObjectMapper().registerModule(new JavaTimeModule()), environment);
    }

    private static void assertHeaders(ProducerRecord<String, String> record, ReservationCommandEvent event) {
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

    private static AccountReservationRequestedEvent requestedEvent() {
        return AccountReservationRequestedEvent.from(transfer());
    }

    private static AccountReservationReleaseRequestedEvent releaseEvent() {
        var transfer = transfer();
        transfer.accountReservationCreated("reservation-request-001", "reservation-001", transfer.correlationId());
        return AccountReservationReleaseRequestedEvent.from(transfer);
    }

    private static Transfer transfer() {
        return Transfer.request(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("12.50"),
                "AED",
                "correlation-001",
                "transfer-request-001",
                "reservation-request-001",
                "posting-request-001");
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

    private static final class RecordingOutbox implements ReservationCommandEventOutbox {
        private final ReservationCommandEvent event;
        private final String persistedPayload;
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> failed = new ArrayList<>();
        private String failureMessage;

        private RecordingOutbox(ReservationCommandEvent event) {
            this(event, null);
        }

        private RecordingOutbox(ReservationCommandEvent event, String persistedPayload) {
            this.event = event;
            this.persistedPayload = persistedPayload;
        }

        @Override
        public boolean recordIfAbsent(ReservationCommandEvent event) {
            return false;
        }

        @Override
        public List<ReservationCommandEvent> claimReady(
                int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of(event);
        }

        @Override
        public void markPublished(ReservationCommandEvent event, UUID claimToken, Instant publishedAt) {
            published.add(event.eventId());
        }

        @Override
        public void markFailed(ReservationCommandEvent event, UUID claimToken, String error, Instant retryAt) {
            failed.add(event.eventId());
            failureMessage = error;
        }

        @Override
        public Optional<String> payloadFor(ReservationCommandEvent event) {
            return Optional.ofNullable(persistedPayload);
        }
    }
}
