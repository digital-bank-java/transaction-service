package com.digitalbank.transactionservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.Test;

class ReservationKafkaEventListenerTest {

    private static final UUID TRANSFER_ID = UUID.randomUUID();
    private static final String EVENT_ID = UUID.randomUUID().toString();
    private static final String CORRELATION_ID = "correlation-001";
    private static final String RESERVATION_REQUEST_ID = "reservation-request-001";
    private static final String RESERVATION_ID = UUID.randomUUID().toString();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RecordingTarget processManager = new RecordingTarget();
    private final ReservationKafkaEventListener listener =
            new ReservationKafkaEventListener(objectMapper, processManager);

    @Test
    void mapsAcceptedFactToProcessManager() {
        var payload = common("AccountReservationAccepted.v1");
        payload.put("reservationId", RESERVATION_ID);
        payload.put("sourceAccountId", UUID.randomUUID().toString());
        payload.put("destinationAccountId", UUID.randomUUID().toString());
        payload.put("amount", "12.50");
        payload.put("currency", "AED");
        payload.put("expiresAt", "2026-09-01T00:05:00Z");
        payload.put("status", "ACTIVE");

        listener.onReservationEvent(record("account.reservation.accepted.v1", payload));

        assertThat(processManager.lastEvent).isInstanceOf(AccountReservationAccepted.class);
    }

    @Test
    void mapsRejectedFactToProcessManager() {
        var payload = common("AccountReservationRejected.v1");
        payload.put("sourceAccountId", UUID.randomUUID().toString());
        payload.put("destinationAccountId", UUID.randomUUID().toString());
        payload.put("amount", "12.50");
        payload.put("currency", "AED");
        payload.put("rejectionCode", "INSUFFICIENT_AVAILABLE_BALANCE");
        payload.put("rejectionReason", "Available balance is lower than requested amount.");

        listener.onReservationEvent(record("account.reservation.rejected.v1", payload));

        assertThat(processManager.lastEvent).isInstanceOf(AccountReservationRejected.class);
    }

    @Test
    void rejectsTopicThatDoesNotMatchPayloadEventType() {
        var payload = common("AccountReservationRejected.v1");
        payload.put("sourceAccountId", UUID.randomUUID().toString());
        payload.put("destinationAccountId", UUID.randomUUID().toString());
        payload.put("amount", "12.50");
        payload.put("currency", "AED");
        payload.put("rejectionCode", "INSUFFICIENT_AVAILABLE_BALANCE");
        payload.put("rejectionReason", "Available balance is lower than requested amount.");

        assertThatThrownBy(() -> listener.onReservationEvent(record("account.reservation.accepted.v1", payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("topic");
    }

    @Test
    void mapsReleasedFactToProcessManager() {
        var payload = common("AccountReservationReleased.v1");
        payload.put("reservationId", RESERVATION_ID);
        payload.put("sourceAccountId", UUID.randomUUID().toString());
        payload.put("amount", "12.50");
        payload.put("currency", "AED");
        payload.put("releaseReason", "LEDGER_POSTING_FAILED");
        payload.put("status", "RELEASED");

        listener.onReservationEvent(record("account.reservation.released.v1", payload));

        assertThat(processManager.lastEvent).isInstanceOf(AccountReservationReleased.class);
    }

    @Test
    void mapsExpiredFactToProcessManager() {
        var payload = common("AccountReservationExpired.v1");
        payload.put("reservationId", RESERVATION_ID);
        payload.put("sourceAccountId", UUID.randomUUID().toString());
        payload.put("amount", "12.50");
        payload.put("currency", "AED");
        payload.put("expiresAt", "2026-09-01T00:05:00Z");
        payload.put("status", "EXPIRED");

        listener.onReservationEvent(record("account.reservation.expired.v1", payload));

        assertThat(processManager.lastEvent).isInstanceOf(AccountReservationExpired.class);
    }

    @Test
    void rejectsFactWithInvalidBusinessFieldBeforeDelegating() {
        var payload = common("AccountReservationAccepted.v1");
        payload.put("reservationId", RESERVATION_ID);
        payload.put("sourceAccountId", UUID.randomUUID().toString());
        payload.put("destinationAccountId", UUID.randomUUID().toString());
        payload.put("amount", BigDecimal.ZERO.toPlainString());
        payload.put("currency", "AED");
        payload.put("expiresAt", "2026-09-01T00:05:00Z");
        payload.put("status", "ACTIVE");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> listener.onReservationEvent(record("account.reservation.accepted.v1", payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    private static ObjectNode common(String eventType) {
        var payload = new ObjectMapper().createObjectNode();
        payload.put("eventId", EVENT_ID);
        payload.put("eventType", eventType);
        payload.put("schemaVersion", "1.0.0");
        payload.put("producer", "account-service");
        payload.put("occurredAt", OCCURRED_AT.toString());
        payload.put("aggregateId", RESERVATION_REQUEST_ID);
        payload.put("correlationId", CORRELATION_ID);
        payload.put("causationId", "account-command-001");
        payload.put("transactionId", TRANSFER_ID.toString());
        payload.put("reservationRequestId", RESERVATION_REQUEST_ID);
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
                RESERVATION_REQUEST_ID,
                payload.toString(),
                headers,
                Optional.empty());
    }

    private static void add(RecordHeaders headers, String name, String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingTarget implements ReservationKafkaEventListener.ReservationEventTarget {
        private Object lastEvent;

        @Override
        public void handle(Object event) {
            lastEvent = event;
        }
    }
}
