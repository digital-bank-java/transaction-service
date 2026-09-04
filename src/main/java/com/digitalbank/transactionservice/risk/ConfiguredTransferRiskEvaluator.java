package com.digitalbank.transactionservice.risk;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ConfiguredTransferRiskEvaluator implements TransferRiskEvaluator {

    private static final String POLICY_REQUIRES_STEP_UP = "POLICY_REQUIRES_STEP_UP";
    private static final String POLICY_DECLINED = "POLICY_DECLINED";

    private final TransferRiskProperties properties;

    public ConfiguredTransferRiskEvaluator(TransferRiskProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public TransferRiskDecision evaluate(TransferRiskIntent intent, Instant now) {
        Objects.requireNonNull(intent, "intent must not be null");
        Objects.requireNonNull(now, "now must not be null");

        var outcome = outcomeFor(intent);
        var reasonCodes = switch (outcome) {
            case ALLOW -> List.<String>of();
            case REQUIRE_STEP_UP -> List.of(POLICY_REQUIRES_STEP_UP);
            case DECLINE -> List.of(POLICY_DECLINED);
        };
        var decisionId = UUID.nameUUIDFromBytes(
                ("transfer-risk-decision:" + properties.policyVersion() + ":" + intent.canonicalForm())
                        .getBytes(StandardCharsets.UTF_8));
        return new TransferRiskDecision(
                decisionId,
                intent.decisionRequestId(),
                intent.transferId(),
                outcome,
                reasonCodes,
                outcome == TransferRiskOutcome.REQUIRE_STEP_UP ? "MFA" : null,
                outcome == TransferRiskOutcome.REQUIRE_STEP_UP ? "TOTP" : null,
                properties.policyVersion(),
                now,
                now.plus(properties.decisionTtl()),
                intent.correlationId());
    }

    private TransferRiskOutcome outcomeFor(TransferRiskIntent intent) {
        if (properties.declinedDestinationClasses().contains(intent.destinationClass())) {
            return TransferRiskOutcome.DECLINE;
        }
        var highValueThreshold = properties.highValueThresholds().get(intent.currency());
        var highValue = highValueThreshold != null && intent.amount().compareTo(highValueThreshold) >= 0;
        if (highValue || properties.stepUpDestinationClasses().contains(intent.destinationClass())) {
            return TransferRiskOutcome.REQUIRE_STEP_UP;
        }
        return TransferRiskOutcome.ALLOW;
    }
}
