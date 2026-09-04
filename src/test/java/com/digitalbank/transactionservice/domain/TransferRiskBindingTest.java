package com.digitalbank.transactionservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.risk.TransferDestinationClass;
import com.digitalbank.transactionservice.risk.TransferRiskDecision;
import com.digitalbank.transactionservice.risk.TransferRiskOutcome;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferRiskBindingTest {

    @Test
    void retainsDecisionAndIntentBindingWhenTransferIsRehydrated() {
        var transferId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var sourceAccountId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var destinationAccountId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        var issuedAt = Instant.parse("2026-09-04T10:15:00Z");
        var decision = new TransferRiskDecision(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "risk-request-001",
                transferId,
                TransferRiskOutcome.REQUIRE_STEP_UP,
                List.of("POLICY_REQUIRES_STEP_UP"),
                "MFA",
                "TOTP",
                "transfer-risk-policy-test",
                issuedAt,
                issuedAt.plusSeconds(300),
                "transfer-correlation-001");

        var transfer = Transfer.request(
                transferId,
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("100.00"),
                "USD",
                "customer-001",
                "WEB",
                TransferDestinationClass.INTERNATIONAL,
                "transfer-correlation-001",
                "transfer-request-001",
                "reservation-request-001",
                "posting-request-001",
                decision);

        var rehydrated = Transfer.rehydrate(
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount(),
                transfer.currency(),
                transfer.customerId(),
                transfer.channel(),
                transfer.destinationClass(),
                transfer.correlationId(),
                transfer.transferRequestId(),
                transfer.reservationRequestId(),
                transfer.postingRequestId(),
                transfer.reservationId(),
                transfer.status(),
                transfer.version(),
                decision);

        assertThat(rehydrated.customerId()).isEqualTo("customer-001");
        assertThat(rehydrated.channel()).isEqualTo("WEB");
        assertThat(rehydrated.destinationClass()).isEqualTo(TransferDestinationClass.INTERNATIONAL);
        assertThat(rehydrated.riskDecision()).isEqualTo(decision);
        assertThat(rehydrated.riskDecision().expiredAt(issuedAt)).isFalse();
    }
}
