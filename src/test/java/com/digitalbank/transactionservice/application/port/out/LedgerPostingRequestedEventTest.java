package com.digitalbank.transactionservice.application.port.out;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.domain.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerPostingRequestedEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void mapsTransferToApprovedLedgerPostingRequestedContract() throws Exception {
        var event = LedgerPostingRequestedEvent.from(transfer());

        assertThat(event.eventId()).isEqualTo(UUID.nameUUIDFromBytes(
                (LedgerPostingRequestedEvent.EVENT_TYPE + ":ledger-posting:posting-request-001")
                        .getBytes(StandardCharsets.UTF_8)));
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(event)))
                .isEqualTo(objectMapper.readTree(expectedJson(event)));
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

    private static Transfer transfer() {
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
        return transfer;
    }
}
