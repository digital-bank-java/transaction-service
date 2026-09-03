package com.digitalbank.transactionservice.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.TestSecurityConfig;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.LedgerPostingRequestedEvent;
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
class LedgerCommandEventOutboxTest {

    @Autowired
    private LedgerCommandEventOutbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from ledger_command_event_outbox");
    }

    @Test
    void recordsOneExactPayloadAndReplaysAfterLeaseRetry() {
        var event = LedgerPostingRequestedEvent.from(transfer());

        assertThat(outbox.recordIfAbsent(event)).isTrue();
        assertThat(outbox.recordIfAbsent(event)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select json_payload from ledger_command_event_outbox where event_id = ?",
                String.class,
                event.eventId())).isEqualTo(json(event));

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

    private static String json(LedgerPostingRequestedEvent event) {
        return """
                {"eventId":"%s","eventType":"LedgerPostingRequested.v1","schemaVersion":"1.0.0","producer":"transaction-service","occurredAt":"%s","aggregateId":"%s","correlationId":"%s","causationId":"%s","transactionId":"%s","reservationRequestId":"%s","reservationId":"%s","postingRequestId":"%s","description":"Transfer posting for transfer-request-001","currency":"AED","effectiveAt":"%s","debitLines":[{"accountId":"22222222-2222-2222-2222-222222222222","amount":"12.50"}],"creditLines":[{"accountId":"33333333-3333-3333-3333-333333333333","amount":"12.50"}]}
                """.formatted(
                event.eventId(),
                event.occurredAt(),
                event.aggregateId(),
                event.correlationId(),
                event.causationId(),
                event.transactionId(),
                event.reservationRequestId(),
                event.reservationId(),
                event.postingRequestId(),
                event.effectiveAt()).strip();
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
