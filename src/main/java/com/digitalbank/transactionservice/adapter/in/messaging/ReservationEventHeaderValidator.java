package com.digitalbank.transactionservice.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class ReservationEventHeaderValidator {

    private static final List<String> REQUIRED_HEADERS = List.of(
            "event-id", "correlation-id", "causation-id", "producer", "schema-version", "occurred-at");

    private ReservationEventHeaderValidator() {}

    static void validate(
            ConsumerRecord<String, String> record,
            JsonNode payload,
            String expectedEventType,
            String expectedProducer) {
        if (record == null || payload == null) {
            throw new IllegalArgumentException("record and payload must not be null");
        }
        for (var headerName : REQUIRED_HEADERS) {
            var header = record.headers().lastHeader(headerName);
            if (header == null || header.value() == null || header.value().length == 0) {
                throw new IllegalArgumentException("Missing required Kafka header: " + headerName);
            }
            var headerValue = new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
            var payloadField = switch (headerName) {
                case "event-id" -> "eventId";
                case "correlation-id" -> "correlationId";
                case "causation-id" -> "causationId";
                case "producer" -> "producer";
                case "schema-version" -> "schemaVersion";
                case "occurred-at" -> "occurredAt";
                default -> throw new IllegalStateException("Unsupported header: " + headerName);
            };
            var payloadValue = requiredText(payload, payloadField);
            if (!headerValue.equals(payloadValue)) {
                throw new IllegalArgumentException("Kafka header does not match payload: " + headerName);
            }
        }

        parseUuid(requiredText(payload, "eventId"), "eventId");
        parseUuid(requiredText(payload, "transactionId"), "transactionId");
        parseInstant(requiredText(payload, "occurredAt"), "occurredAt");
        requiredText(payload, "eventType");
        requiredText(payload, "schemaVersion");
        requiredText(payload, "producer");
        requiredText(payload, "aggregateId");
        requiredText(payload, "correlationId");
        requiredText(payload, "causationId");
        requiredText(payload, "reservationRequestId");
        if (!expectedEventType.equals(requiredText(payload, "eventType"))) {
            throw new IllegalArgumentException("Unexpected reservation event type for topic");
        }
        if (!"1.0.0".equals(requiredText(payload, "schemaVersion"))) {
            throw new IllegalArgumentException("Unsupported reservation schema version");
        }
        if (!expectedProducer.equals(requiredText(payload, "producer"))) {
            throw new IllegalArgumentException("Unexpected reservation event producer");
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        var value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("Missing required reservation field: " + field);
        }
        return value.textValue();
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " must be a UUID", exception);
        }
    }

    private static Instant parseInstant(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be an instant", exception);
        }
    }
}
