package com.digitalbank.transactionservice.application.port.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowMessageTest {

    private static final UUID TRANSFER_ID = UUID.randomUUID();

    @Test
    void requestTransferRejectsBlankCorrelationId() {
        assertThatThrownBy(() -> new RequestTransferCommand(
                TRANSFER_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("10.00"),
                "AED",
                " ",
                "transfer-request-001",
                "reservation-request-001",
                "posting-request-001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ledgerCompletionRequiresPostingRequestId() {
        assertThatThrownBy(() -> new LedgerPostingCompleted(
                TRANSFER_ID, "event-001", "correlation-001", " ", "posting-001"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
