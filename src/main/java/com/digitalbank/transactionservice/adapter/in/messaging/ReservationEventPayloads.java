package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class ReservationEventPayloads {

    private static final String PRODUCER = "account-service";
    private static final String SCHEMA_VERSION = "1.0.0";

    private ReservationEventPayloads() {}

    static String eventType(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        try {
            if (record == null || record.value() == null) {
                throw new ReservationEventValidationException("Reservation Kafka record must contain a payload");
            }
            var payload = mapper.readTree(record.value());
            if (payload == null || !payload.isObject()) {
                throw new ReservationEventValidationException("Reservation Kafka payload must be an object");
            }
            return text(payload, "eventType");
        } catch (JsonProcessingException exception) {
            throw new ReservationEventValidationException("Invalid reservation Kafka JSON payload", exception);
        } catch (IllegalArgumentException exception) {
            if (exception instanceof ReservationEventValidationException validationException) {
                throw validationException;
            }
            throw new ReservationEventValidationException("Invalid reservation Kafka payload", exception);
        }
    }

    static AccountReservationAccepted accepted(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        var payload = common(record, mapper, "AccountReservationAccepted.v1");
        var reservationId = uuid(payload, "reservationId");
        uuid(payload, "sourceAccountId");
        uuid(payload, "destinationAccountId");
        positiveAmount(payload);
        currency(payload);
        instant(payload, "expiresAt");
        equalsText(payload, "status", "ACTIVE");
        return new AccountReservationAccepted(
                uuid(payload, "transactionId"), text(payload, "eventId"), text(payload, "correlationId"),
                text(payload, "reservationRequestId"), reservationId.toString());
    }

    static AccountReservationRejected rejected(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        var payload = common(record, mapper, "AccountReservationRejected.v1");
        uuid(payload, "sourceAccountId");
        uuid(payload, "destinationAccountId");
        positiveAmount(payload);
        currency(payload);
        oneOf(payload, "rejectionCode", Set.of(
                "ACCOUNT_NOT_FOUND", "ACCOUNT_NOT_ACTIVE", "CURRENCY_MISMATCH", "INSUFFICIENT_AVAILABLE_BALANCE",
                "VALIDATION_ERROR", "CONFLICT"));
        var reason = text(payload, "rejectionReason");
        return new AccountReservationRejected(
                uuid(payload, "transactionId"), text(payload, "eventId"), text(payload, "correlationId"),
                text(payload, "reservationRequestId"), reason);
    }

    static AccountReservationReleased released(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        var payload = common(record, mapper, "AccountReservationReleased.v1");
        var reservationId = uuid(payload, "reservationId");
        uuid(payload, "sourceAccountId");
        positiveAmount(payload);
        currency(payload);
        oneOf(payload, "releaseReason", Set.of(
                "LEDGER_POSTING_FAILED", "TRANSFER_CANCELED", "TRANSFER_TIMEOUT", "MANUAL_COMPENSATION"));
        equalsText(payload, "status", "RELEASED");
        return new AccountReservationReleased(
                uuid(payload, "transactionId"), text(payload, "eventId"), text(payload, "correlationId"),
                text(payload, "reservationRequestId"), reservationId.toString());
    }

    static AccountReservationExpired expired(ConsumerRecord<String, String> record, ObjectMapper mapper) {
        var payload = common(record, mapper, "AccountReservationExpired.v1");
        var reservationId = uuid(payload, "reservationId");
        uuid(payload, "sourceAccountId");
        positiveAmount(payload);
        currency(payload);
        instant(payload, "expiresAt");
        equalsText(payload, "status", "EXPIRED");
        return new AccountReservationExpired(
                uuid(payload, "transactionId"), text(payload, "eventId"), text(payload, "correlationId"),
                text(payload, "reservationRequestId"), reservationId.toString());
    }

    private static JsonNode common(
            ConsumerRecord<String, String> record, ObjectMapper mapper, String expectedEventType) {
        try {
            var payload = mapper.readTree(record.value());
            if (payload == null || !payload.isObject()) {
                throw new ReservationEventValidationException("Reservation Kafka payload must be an object");
            }
            ReservationEventHeaderValidator.validate(record, payload, expectedEventType, PRODUCER);
            if (!text(payload, "aggregateId").equals(text(payload, "reservationRequestId"))) {
                throw new ReservationEventValidationException("aggregateId must match reservationRequestId");
            }
            return payload;
        } catch (JsonProcessingException exception) {
            throw new ReservationEventValidationException("Invalid reservation Kafka JSON payload", exception);
        } catch (IllegalArgumentException exception) {
            if (exception instanceof ReservationEventValidationException validationException) {
                throw validationException;
            }
            throw new ReservationEventValidationException(exception.getMessage(), exception);
        }
    }

    private static String text(JsonNode payload, String field) {
        var value = payload.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new ReservationEventValidationException("Missing required reservation field: " + field);
        }
        return value.textValue().trim();
    }

    private static UUID uuid(JsonNode payload, String field) {
        try {
            return UUID.fromString(text(payload, field));
        } catch (IllegalArgumentException exception) {
            throw new ReservationEventValidationException(field + " must be a UUID", exception);
        }
    }

    private static Instant instant(JsonNode payload, String field) {
        try {
            return Instant.parse(text(payload, field));
        } catch (DateTimeParseException exception) {
            throw new ReservationEventValidationException(field + " must be an instant", exception);
        }
    }

    private static void positiveAmount(JsonNode payload) {
        try {
            if (new BigDecimal(text(payload, "amount")).signum() <= 0) {
                throw new ReservationEventValidationException("amount must be positive");
            }
        } catch (NumberFormatException exception) {
            throw new ReservationEventValidationException("amount must be a decimal", exception);
        }
    }

    private static void currency(JsonNode payload) {
        var value = text(payload, "currency");
        if (!value.matches("[A-Z]{3}")) {
            throw new ReservationEventValidationException("currency must be an uppercase ISO-4217 code");
        }
    }

    private static void equalsText(JsonNode payload, String field, String expected) {
        if (!expected.equals(text(payload, field))) {
            throw new ReservationEventValidationException(field + " must be " + expected);
        }
    }

    private static void oneOf(JsonNode payload, String field, Set<String> allowed) {
        if (!allowed.contains(text(payload, field))) {
            throw new ReservationEventValidationException("Unsupported " + field);
        }
    }
}
