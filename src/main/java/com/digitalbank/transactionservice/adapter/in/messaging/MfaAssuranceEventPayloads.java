package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.port.in.MfaAssuranceGranted;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class MfaAssuranceEventPayloads {

    private static final String EVENT_TYPE = "MfaAssuranceGranted.v1";
    private static final String PRODUCER = "mfa-service";
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final Duration MAX_OCCURRED_AT_PRECISION_DRIFT = Duration.ofNanos(1_000);

    private MfaAssuranceEventPayloads() {}

    static MfaAssuranceGranted granted(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        try {
            if (record == null || record.value() == null) {
                throw new MfaAssuranceEventValidationException("MFA assurance Kafka record must contain a payload");
            }
            var payload = mapper.readTree(record.value());
            if (payload == null || !payload.isObject()) {
                throw new MfaAssuranceEventValidationException("MFA assurance Kafka payload must be an object");
            }
            validateHeaders(record, payload);
            currency(payload);
            return new MfaAssuranceGranted(
                    uuid(payload, "transferId"), text(payload, "eventId"), text(payload, "correlationId"),
                    text(payload, "decisionRequestId"), text(payload, "reservationRequestId"),
                    uuid(payload, "decisionId"), text(payload, "customerId"), text(payload, "challengeId"),
                    text(payload, "assuranceType"), uuid(payload, "sourceAccountId"),
                    uuid(payload, "destinationAccountId"), decimal(payload, "amount"), text(payload, "currency"),
                    text(payload, "challengeType"), instant(payload, "grantedAt"), instant(payload, "expiresAt"),
                    text(payload, "policyVersion"));
        } catch (JsonProcessingException exception) {
            throw new MfaAssuranceEventValidationException("Invalid MFA assurance Kafka JSON payload", exception);
        } catch (IllegalArgumentException exception) {
            if (exception instanceof MfaAssuranceEventValidationException validationException) {
                throw validationException;
            }
            throw new MfaAssuranceEventValidationException(exception.getMessage(), exception);
        }
    }

    private static void validateHeaders(ConsumerRecord<String, String> record, JsonNode payload) {
        for (var headerName : new String[] {"event-id", "correlation-id", "causation-id", "producer", "schema-version", "occurred-at"}) {
            var header = record.headers().lastHeader(headerName);
            if (header == null || header.value() == null || header.value().length == 0) {
                throw new MfaAssuranceEventValidationException("Missing required Kafka header: " + headerName);
            }
            var expected = switch (headerName) {
                case "event-id" -> "eventId";
                case "correlation-id" -> "correlationId";
                case "causation-id" -> "causationId";
                case "producer" -> "producer";
                case "schema-version" -> "schemaVersion";
                case "occurred-at" -> "occurredAt";
                default -> throw new IllegalStateException("Unsupported header: " + headerName);
            };
            var value = new String(header.value(), java.nio.charset.StandardCharsets.UTF_8);
            var matches = "occurredAt".equals(expected)
                    ? withinPrecision(instantValue(value, headerName), instant(payload, expected))
                    : value.equals(text(payload, expected));
            if (!matches) {
                throw new MfaAssuranceEventValidationException("Kafka header does not match payload: " + headerName);
            }
        }
        if (!EVENT_TYPE.equals(text(payload, "eventType"))) {
            throw new MfaAssuranceEventValidationException("Unexpected MFA assurance event type");
        }
        if (!SCHEMA_VERSION.equals(text(payload, "schemaVersion"))) {
            throw new MfaAssuranceEventValidationException("Unsupported MFA assurance schema version");
        }
        if (!PRODUCER.equals(text(payload, "producer"))) {
            throw new MfaAssuranceEventValidationException("Unexpected MFA assurance event producer");
        }
        if (!text(payload, "aggregateId").equals(text(payload, "transactionId"))
                || !text(payload, "aggregateId").equals(text(payload, "transferId"))) {
            throw new MfaAssuranceEventValidationException("aggregateId must match transactionId and transferId");
        }
        if (!"MFA".equals(text(payload, "assuranceType"))) {
            throw new MfaAssuranceEventValidationException("assuranceType must be MFA");
        }
        uuid(payload, "eventId");
        instant(payload, "occurredAt");
    }

    private static String text(JsonNode payload, String field) {
        var value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new MfaAssuranceEventValidationException("Missing required MFA assurance field: " + field);
        }
        return value.textValue().trim();
    }

    private static UUID uuid(JsonNode payload, String field) {
        try {
            return UUID.fromString(text(payload, field));
        } catch (IllegalArgumentException exception) {
            throw new MfaAssuranceEventValidationException(field + " must be a UUID", exception);
        }
    }

    private static Instant instant(JsonNode payload, String field) {
        try {
            return Instant.parse(text(payload, field));
        } catch (DateTimeParseException exception) {
            throw new MfaAssuranceEventValidationException(field + " must be an instant", exception);
        }
    }

    private static Instant instantValue(String value, String field) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new MfaAssuranceEventValidationException(field + " must be an instant", exception);
        }
    }

    private static boolean withinPrecision(Instant headerValue, Instant payloadValue) {
        return Duration.between(headerValue, payloadValue).abs().compareTo(MAX_OCCURRED_AT_PRECISION_DRIFT) <= 0;
    }

    private static BigDecimal decimal(JsonNode payload, String field) {
        try {
            var raw = text(payload, field);
            if (!raw.matches("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?$")) {
                throw new MfaAssuranceEventValidationException(
                        field + " must be a decimal with no more than 4 places");
            }
            var value = new BigDecimal(raw);
            if (value.signum() <= 0) {
                throw new MfaAssuranceEventValidationException(field + " must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new MfaAssuranceEventValidationException(field + " must be a decimal", exception);
        }
    }

    private static void currency(JsonNode payload) {
        if (!text(payload, "currency").matches("[A-Z]{3}")) {
            throw new MfaAssuranceEventValidationException("currency must be an uppercase ISO-4217 code");
        }
    }
}
