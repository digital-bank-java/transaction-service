package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class LedgerEventPayloads {

    private static final String PRODUCER = "ledger-service";

    private LedgerEventPayloads() {}

    static LedgerPostingCompleted completed(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        var payload = common(record, mapper, "LedgerPostingCompleted.v1");
        if (!text(payload, "aggregateId").equals(text(payload, "postingId"))) {
            throw new LedgerEventValidationException("aggregateId must match postingId");
        }
        currency(payload);
        lines(payload);
        return new LedgerPostingCompleted(
                uuid(payload, "transactionId"),
                text(payload, "eventId"),
                text(payload, "correlationId"),
                text(payload, "causationId"),
                text(payload, "postingRequestId"));
    }

    static LedgerPostingFailed failed(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        var payload = common(record, mapper, "LedgerPostingFailed.v1");
        if (!text(payload, "aggregateId").equals(text(payload, "postingRequestId"))) {
            throw new LedgerEventValidationException("aggregateId must match postingRequestId");
        }
        oneOf(payload, "failureCode", Set.of("VALIDATION_ERROR", "CONFLICT", "ACCOUNTING_ERROR"));
        return new LedgerPostingFailed(
                uuid(payload, "transactionId"),
                text(payload, "eventId"),
                text(payload, "correlationId"),
                text(payload, "causationId"),
                text(payload, "postingRequestId"),
                text(payload, "failureReason"));
    }

    private static JsonNode common(ConsumerRecord<String, String> record, ObjectMapper mapper, String expectedEventType) {
        try {
            if (record == null || record.value() == null) {
                throw new LedgerEventValidationException("Ledger Kafka record must contain a payload");
            }
            var payload = mapper.readTree(record.value());
            if (payload == null || !payload.isObject()) {
                throw new LedgerEventValidationException("Ledger Kafka payload must be an object");
            }
            LedgerEventHeaderValidator.validate(record, payload, expectedEventType, PRODUCER);
            return payload;
        } catch (JsonProcessingException exception) {
            throw new LedgerEventValidationException("Invalid ledger Kafka JSON payload", exception);
        } catch (IllegalArgumentException exception) {
            if (exception instanceof LedgerEventValidationException validationException) {
                throw validationException;
            }
            throw new LedgerEventValidationException(exception.getMessage(), exception);
        }
    }

    private static String text(JsonNode payload, String field) {
        var value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new LedgerEventValidationException("Missing required ledger field: " + field);
        }
        return value.textValue().trim();
    }

    private static UUID uuid(JsonNode payload, String field) {
        try {
            return UUID.fromString(text(payload, field));
        } catch (IllegalArgumentException exception) {
            throw new LedgerEventValidationException(field + " must be a UUID", exception);
        }
    }

    private static void currency(JsonNode payload) {
        var value = text(payload, "currency");
        if (!value.matches("[A-Z]{3}")) {
            throw new LedgerEventValidationException("currency must be an uppercase ISO-4217 code");
        }
    }

    private static void lines(JsonNode payload) {
        var lines = payload.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            throw new LedgerEventValidationException("lines must contain at least one ledger line");
        }
        var types = new java.util.HashSet<String>();
        lines.forEach(line -> {
            if (line == null || !line.isObject()) {
                throw new LedgerEventValidationException("ledger lines must be objects");
            }
            try {
                UUID.fromString(text(line, "accountId"));
            } catch (IllegalArgumentException exception) {
                throw new LedgerEventValidationException("accountId must be a UUID", exception);
            }
            var type = text(line, "lineType");
            if (!Set.of("DEBIT", "CREDIT").contains(type)) {
                throw new LedgerEventValidationException("Unsupported lineType");
            }
            types.add(type);
            try {
                if (new BigDecimal(text(line, "amount")).signum() <= 0) {
                    throw new LedgerEventValidationException("ledger line amount must be positive");
                }
            } catch (NumberFormatException exception) {
                throw new LedgerEventValidationException("ledger line amount must be a decimal", exception);
            }
        });
        if (!types.contains("DEBIT") || !types.contains("CREDIT")) {
            throw new LedgerEventValidationException("lines must contain at least one debit and one credit");
        }
    }

    private static void oneOf(JsonNode payload, String field, Set<String> allowed) {
        if (!allowed.contains(text(payload, field))) {
            throw new LedgerEventValidationException("Unsupported " + field);
        }
    }
}
