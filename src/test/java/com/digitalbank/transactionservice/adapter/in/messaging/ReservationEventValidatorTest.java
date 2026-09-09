package com.digitalbank.transactionservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class ReservationEventValidatorTest {

    private static final String EVENT_ID = "20000000-0000-4000-8000-000000000001";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsMatchingGovernedHeadersAndEnvelope() throws Exception {
        var payload = objectMapper.readTree(payloadJson());

        assertThatCode(() -> ReservationEventHeaderValidator.validate(
                        record(headers()), payload, "AccountReservationAccepted.v1", "account-service"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingRequiredHeader() throws Exception {
        var headers = headers();
        headers.remove("event-id");

        assertThatThrownBy(() -> ReservationEventHeaderValidator.validate(
                        record(headers), objectMapper.readTree(payloadJson()),
                        "AccountReservationAccepted.v1", "account-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event-id");
    }

    @Test
    void rejectsHeaderThatDoesNotMatchPayload() throws Exception {
        var headers = headers();
        headers.remove("correlation-id");
        headers.add("correlation-id", "wrong-correlation".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> ReservationEventHeaderValidator.validate(
                        record(headers), objectMapper.readTree(payloadJson()),
                        "AccountReservationAccepted.v1", "account-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation-id");
    }

    @Test
    void acceptsEquivalentOccurredAtPrecision() throws Exception {
        var headers = headers();
        headers.remove("occurred-at");
        headers.add("occurred-at", "2026-08-31T10:15:31.123456Z".getBytes(StandardCharsets.UTF_8));
        var payload = objectMapper.readTree(payloadJson().replace(
                "2026-08-31T10:15:31Z", "2026-08-31T10:15:31.123456463Z"));

        assertThatCode(() -> ReservationEventHeaderValidator.validate(
                        record(headers), payload, "AccountReservationAccepted.v1", "account-service"))
                .doesNotThrowAnyException();
    }

    private ConsumerRecord<String, String> record(RecordHeaders headers) {
        return new ConsumerRecord<>("account.reservation.accepted.v1", 0, 0L, 0L, null, 0, 0,
                "source-account", payloadJson(), headers, java.util.Optional.empty());
    }

    private RecordHeaders headers() {
        var headers = new RecordHeaders();
        add(headers, "event-id", EVENT_ID);
        add(headers, "correlation-id", "transfer-correlation-001");
        add(headers, "causation-id", "10000000-0000-4000-8000-000000000001");
        add(headers, "producer", "account-service");
        add(headers, "schema-version", "1.0.0");
        add(headers, "occurred-at", "2026-08-31T10:15:31Z");
        return headers;
    }

    private static void add(RecordHeaders headers, String name, String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String payloadJson() {
        return """
                {"eventId":"%s","eventType":"AccountReservationAccepted.v1","schemaVersion":"1.0.0","producer":"account-service","occurredAt":"2026-08-31T10:15:31Z","aggregateId":"reservation-request-001","correlationId":"transfer-correlation-001","causationId":"10000000-0000-4000-8000-000000000001","transactionId":"11111111-1111-1111-1111-111111111111","reservationRequestId":"reservation-request-001","reservationId":"60000000-0000-4000-8000-000000000001","sourceAccountId":"22222222-2222-2222-2222-222222222222","destinationAccountId":"33333333-3333-3333-3333-333333333333","amount":"12.50","currency":"AED","expiresAt":"2026-08-31T10:20:30Z","status":"ACTIVE"}
                """.formatted(EVENT_ID);
    }
}
