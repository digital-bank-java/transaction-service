package com.digitalbank.transactionservice.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.TestSecurityConfig;
import com.digitalbank.transactionservice.application.port.out.TransferCompletedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEventOutbox;
import com.digitalbank.transactionservice.domain.Transfer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestSecurityConfig.class)
class TransferTerminalEventOutboxTest {

    @Autowired
    private TransferTerminalEventOutbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from transfer_terminal_event_outbox");
    }

    @Test
    void recordsOneTerminalEventAndRetriesAfterLeaseFailure() {
        var event = completedEvent();

        assertThat(outbox.recordIfAbsent(event)).isTrue();
        assertThat(outbox.recordIfAbsent(event)).isFalse();
        var stored = jdbcTemplate.queryForObject(
                "select event_type, status, json_payload from transfer_terminal_event_outbox where event_id = ?",
                (result, rowNum) -> new StoredEvent(
                        result.getString("event_type"),
                        result.getString("status"),
                        result.getString("json_payload")),
                event.eventId());
        assertThat(stored)
                .isEqualTo(new StoredEvent("TransferCompleted.v1", "COMPLETED", json(event)));

        var claimToken = UUID.randomUUID();
        var now = Instant.now();
        assertThat(outbox.claimReady(1, now, claimToken, now.plusSeconds(60)))
                .singleElement()
                .isEqualTo(event);

        outbox.markFailed(event, claimToken, "broker unavailable", now.plusSeconds(5));
        assertThat(outbox.claimReady(1, now, UUID.randomUUID(), now.plusSeconds(60))).isEmpty();
        assertThat(outbox.claimReady(1, now.plusSeconds(6), UUID.randomUUID(), now.plusSeconds(66)))
                .singleElement()
                .isEqualTo(event);
    }

    private static TransferCompletedEvent completedEvent() {
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
        transfer.ledgerPostingCompleted("posting-request-001", "transfer-correlation-001");
        return TransferCompletedEvent.from(transfer, "ledger-event-001", "posting-001", Instant.parse("2026-09-07T00:00:00Z"));
    }

    private static String json(TransferTerminalEvent event) {
        return """
                {"eventId":"%s","eventType":"TransferCompleted.v1","schemaVersion":"1.0.0","producer":"transaction-service","occurredAt":"2026-09-07T00:00:00Z","aggregateId":"11111111-1111-1111-1111-111111111111","correlationId":"transfer-correlation-001","causationId":"ledger-event-001","transactionId":"11111111-1111-1111-1111-111111111111","sourceAccountId":"22222222-2222-2222-2222-222222222222","destinationAccountId":"33333333-3333-3333-3333-333333333333","amount":"12.50","currency":"AED","transferRequestId":"transfer-request-001","reservationRequestId":"reservation-request-001","reservationId":"reservation-001","postingRequestId":"posting-request-001","postingId":"posting-001","status":"COMPLETED","failureStage":null,"failureCode":null,"failureReason":null,"compensationStatus":null,"manualReviewRequired":null}
                """.formatted(event.eventId()).strip();
    }

    private record StoredEvent(String eventType, String status, String jsonPayload) {}
}
