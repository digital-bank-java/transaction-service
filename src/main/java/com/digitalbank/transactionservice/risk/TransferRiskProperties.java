package com.digitalbank.transactionservice.risk;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "transaction.risk")
public record TransferRiskProperties(
        String policyVersion,
        Duration decisionTtl,
        Map<String, BigDecimal> highValueThresholds,
        Set<TransferDestinationClass> stepUpDestinationClasses,
        Set<TransferDestinationClass> declinedDestinationClasses) {

    public TransferRiskProperties {
        policyVersion = requireText(policyVersion == null ? "transfer-risk-policy-2026-09" : policyVersion, "policyVersion");
        decisionTtl = decisionTtl == null ? Duration.ofMinutes(5) : decisionTtl;
        if (decisionTtl.isNegative() || decisionTtl.isZero() || decisionTtl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("decisionTtl must be greater than zero and at most 15 minutes");
        }

        var thresholds = new LinkedHashMap<String, BigDecimal>();
        if (highValueThresholds != null) {
            highValueThresholds.forEach((currency, threshold) -> {
                var normalizedCurrency = requireText(currency, "highValueThreshold currency").toUpperCase(Locale.ROOT);
                if (normalizedCurrency.length() != 3) {
                    throw new IllegalArgumentException("highValueThreshold currency must have length 3");
                }
                Objects.requireNonNull(threshold, "highValueThreshold must not be null");
                if (threshold.signum() <= 0) {
                    throw new IllegalArgumentException("highValueThreshold must be positive");
                }
                thresholds.put(normalizedCurrency, threshold.stripTrailingZeros());
            });
        }
        highValueThresholds = Map.copyOf(thresholds);
        stepUpDestinationClasses = immutableClasses(stepUpDestinationClasses);
        declinedDestinationClasses = immutableClasses(declinedDestinationClasses);
        if (!Set.copyOf(stepUpDestinationClasses).stream().noneMatch(declinedDestinationClasses::contains)) {
            throw new IllegalArgumentException("a destination class cannot be both step-up and declined");
        }
    }

    private static Set<TransferDestinationClass> immutableClasses(Set<TransferDestinationClass> values) {
        return values == null || values.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(values));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
