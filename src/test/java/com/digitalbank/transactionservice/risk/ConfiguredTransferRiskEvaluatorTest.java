package com.digitalbank.transactionservice.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfiguredTransferRiskEvaluatorTest {

    private static final UUID TRANSFER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DESTINATION_ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-09-04T10:15:00Z");

    @Test
    void requiresStepUpForInternationalAndHighValueTransfersAndDeclinesBlockedClass() {
        var evaluator = new ConfiguredTransferRiskEvaluator(new TransferRiskProperties(
                "transfer-risk-policy-test",
                Duration.ofMinutes(5),
                Map.of("USD", new BigDecimal("1000.00")),
                Set.of(TransferDestinationClass.INTERNATIONAL),
                Set.of(TransferDestinationClass.DOMESTIC)));

        var international = evaluator.evaluate(intent("risk-international", "100.00", TransferDestinationClass.INTERNATIONAL), NOW);
        var highValue = evaluator.evaluate(intent("risk-high-value", "1000.01", TransferDestinationClass.INTERNAL), NOW);
        var blocked = evaluator.evaluate(intent("risk-blocked", "100.00", TransferDestinationClass.DOMESTIC), NOW);

        assertThat(international.outcome()).isEqualTo(TransferRiskOutcome.REQUIRE_STEP_UP);
        assertThat(international.reasonCodes()).containsExactly("POLICY_REQUIRES_STEP_UP");
        assertThat(international.requiredAssurance()).isEqualTo("MFA");
        assertThat(international.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        assertThat(highValue.outcome()).isEqualTo(TransferRiskOutcome.REQUIRE_STEP_UP);
        assertThat(highValue.reasonCodes()).containsExactly("POLICY_REQUIRES_STEP_UP");
        assertThat(blocked.outcome()).isEqualTo(TransferRiskOutcome.DECLINE);
        assertThat(blocked.reasonCodes()).containsExactly("POLICY_DECLINED");
    }

    private static TransferRiskIntent intent(String requestId, String amount, TransferDestinationClass destinationClass) {
        return new TransferRiskIntent(
                TRANSFER_ID,
                requestId,
                "customer-001",
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal(amount),
                "usd",
                "WEB",
                destinationClass,
                "transfer-correlation-001");
    }
}
