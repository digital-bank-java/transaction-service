package com.digitalbank.transactionservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

class LedgerKafkaEventListenerTest {

    private static final UUID TRANSFER_ID = UUID.randomUUID();
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String CORRELATION_ID = "correlation-001";
    private static final String RESERVATION_REQUEST_ID = "reservation-request-001";
    private static final String POSTING_REQUEST_ID = "posting-request-001";
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T00:00:00Z");
    private static final String COMPLETED_TOPIC = "ledger.posting.completed.v1";
    private static final String FAILED_TOPIC = "ledger.posting.failed.v1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingTarget processManager = new RecordingTarget();
    private final LedgerKafkaEventListener listener =
            new LedgerKafkaEventListener(objectMapper, processManager, COMPLETED_TOPIC, FAILED_TOPIC);

    @Test
    void mapsCompletedFactToProcessManager() {
        var payload = completedPayload();

        listener.onLedgerEvent(record(COMPLETED_TOPIC, payload));

        assertThat(processManager.lastEvent).isInstanceOf(LedgerPostingCompleted.class);
    }

    @Test
    void mapsFailedFactToProcessManager() {
        var payload = failedPayload();

        listener.onLedgerEvent(record(FAILED_TOPIC, payload));

        assertThat(processManager.lastEvent).isInstanceOf(LedgerPostingFailed.class);
    }

    @Test
    void mapsInternalErrorFailedFactToProcessManager() {
        var payload = failedPayload();
        payload.put("failureCode", "INTERNAL_ERROR");

        listener.onLedgerEvent(record(FAILED_TOPIC, payload));

        assertThat(processManager.lastEvent).isInstanceOf(LedgerPostingFailed.class);
    }

    @Test
    void rejectsFailedFactWithOverlongReason() {
        var payload = failedPayload();
        payload.put("failureReason", "x".repeat(501));

        assertThatThrownBy(() -> listener.onLedgerEvent(record(FAILED_TOPIC, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failureReason");
    }

    @Test
    void rejectsCompletedFactWithOutOfContractLineAmount() {
        var payload = completedPayload();
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.withArray("lines").get(0))
                .put("amount", "1.00001");

        assertThatThrownBy(() -> listener.onLedgerEvent(record(COMPLETED_TOPIC, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void rejectsTopicThatDoesNotMatchPayloadEventType() {
        var payload = failedPayload();

        assertThatThrownBy(() -> listener.onLedgerEvent(record(COMPLETED_TOPIC, payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    private ObjectNode completedPayload() {
        var payload = common("LedgerPostingCompleted.v1");
        payload.put("aggregateId", "60000000-0000-4000-8000-000000000001");
        payload.put("postingId", "60000000-0000-4000-8000-000000000001");
        payload.put("currency", "AED");
        var lines = payload.putArray("lines");
        lines.addObject()
                .put("accountId", UUID.randomUUID().toString())
                .put("lineType", "DEBIT")
                .put("amount", "12.50");
        lines.addObject()
                .put("accountId", UUID.randomUUID().toString())
                .put("lineType", "CREDIT")
                .put("amount", "12.50");
        return payload;
    }

    private ObjectNode failedPayload() {
        var payload = common("LedgerPostingFailed.v1");
        payload.put("aggregateId", POSTING_REQUEST_ID);
        payload.put("failureCode", "VALIDATION_ERROR");
        payload.put("failureReason", "Posting lines must contain at least one debit and one credit.");
        return payload;
    }

    private ObjectNode common(String eventType) {
        var payload = objectMapper.createObjectNode();
        payload.put("eventId", EVENT_ID);
        payload.put("eventType", eventType);
        payload.put("schemaVersion", "1.0.0");
        payload.put("producer", "ledger-service");
        payload.put("occurredAt", OCCURRED_AT.toString());
        payload.put("correlationId", CORRELATION_ID);
        payload.put("causationId", "command-001");
        payload.put("transactionId", TRANSFER_ID.toString());
        payload.put("reservationRequestId", RESERVATION_REQUEST_ID);
        payload.put("postingRequestId", POSTING_REQUEST_ID);
        return payload;
    }

    private ConsumerRecord<String, String> record(String topic, ObjectNode payload) {
        var headers = new RecordHeaders();
        add(headers, "event-id", payload.get("eventId").textValue());
        add(headers, "correlation-id", payload.get("correlationId").textValue());
        add(headers, "causation-id", payload.get("causationId").textValue());
        add(headers, "producer", payload.get("producer").textValue());
        add(headers, "schema-version", payload.get("schemaVersion").textValue());
        add(headers, "occurred-at", payload.get("occurredAt").textValue());
        return new ConsumerRecord<>(
                topic,
                0,
                0L,
                ConsumerRecord.NO_TIMESTAMP,
                TimestampType.NO_TIMESTAMP_TYPE,
                -1,
                -1,
                POSTING_REQUEST_ID,
                payload.toString(),
                headers,
                Optional.empty());
    }

    private static void add(RecordHeaders headers, String name, String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingTarget implements LedgerKafkaEventListener.LedgerEventTarget {
        private Object lastEvent;

        @Override
        public void handle(Object event) {
            lastEvent = event;
        }
    }
}
