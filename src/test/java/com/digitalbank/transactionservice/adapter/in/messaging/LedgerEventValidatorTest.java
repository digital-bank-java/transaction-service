package com.digitalbank.transactionservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

class LedgerEventValidatorTest {

    private static final String EVENT_ID = "20000000-0000-4000-8000-000000000001";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsMatchingGovernedHeadersAndEnvelope() throws Exception {
        var payload = objectMapper.readTree(completedPayloadJson());

        assertThatCode(() -> LedgerEventHeaderValidator.validate(
                record("ledger.posting.completed.v1", headers(), completedPayloadJson()),
                payload,
                "LedgerPostingCompleted.v1",
                "ledger-service")).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingRequiredHeader() throws Exception {
        var headers = headers();
        headers.remove("event-id");

        assertThatThrownBy(() -> LedgerEventHeaderValidator.validate(
                record("ledger.posting.completed.v1", headers, completedPayloadJson()),
                objectMapper.readTree(completedPayloadJson()),
                "LedgerPostingCompleted.v1",
                "ledger-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event-id");
    }

    @Test
    void rejectsHeaderThatDoesNotMatchPayload() throws Exception {
        var headers = headers();
        headers.remove("correlation-id");
        headers.add("correlation-id", "wrong-correlation".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> LedgerEventHeaderValidator.validate(
                record("ledger.posting.completed.v1", headers, completedPayloadJson()),
                objectMapper.readTree(completedPayloadJson()),
                "LedgerPostingCompleted.v1",
                "ledger-service"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correlation-id");
    }

    private ConsumerRecord<String, String> record(String topic, RecordHeaders headers, String payloadJson) {
        return new ConsumerRecord<>(topic, 0, 0L, 0L, null, 0, 0,
                "posting-request-001", payloadJson, headers, Optional.empty());
    }

    private RecordHeaders headers() {
        var headers = new RecordHeaders();
        add(headers, "event-id", EVENT_ID);
        add(headers, "correlation-id", "transfer-correlation-001");
        add(headers, "causation-id", "command-001");
        add(headers, "producer", "ledger-service");
        add(headers, "schema-version", "1.0.0");
        add(headers, "occurred-at", "2026-08-31T10:15:31Z");
        return headers;
    }

    private static void add(RecordHeaders headers, String name, String value) {
        headers.add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static String completedPayloadJson() {
        return """
                {"eventId":"%s","eventType":"LedgerPostingCompleted.v1","schemaVersion":"1.0.0","producer":"ledger-service","occurredAt":"2026-08-31T10:15:31Z","aggregateId":"60000000-0000-4000-8000-000000000001","correlationId":"transfer-correlation-001","causationId":"command-001","transactionId":"11111111-1111-1111-1111-111111111111","reservationRequestId":"reservation-request-001","postingId":"60000000-0000-4000-8000-000000000001","postingRequestId":"posting-request-001","currency":"AED","lines":[{"accountId":"22222222-2222-2222-2222-222222222222","lineType":"DEBIT","amount":"12.50"},{"accountId":"33333333-3333-3333-3333-333333333333","lineType":"CREDIT","amount":"12.50"}]}
                """.formatted(EVENT_ID);
    }
}
