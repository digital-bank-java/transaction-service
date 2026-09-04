package com.digitalbank.transactionservice.adapter.out.messaging;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class MfaAssuranceKafkaTransportSecurityBoundary {

    MfaAssuranceKafkaTransportSecurityBoundary() {}

    static void validate(Environment environment) {
        var protocol = environment.getProperty("transaction.events.mfa-assurance.security.protocol");
        if (protocol == null || protocol.isBlank()) {
            protocol = environment.getProperty("spring.kafka.properties[security.protocol]",
                    environment.getProperty("spring.kafka.properties.security.protocol", "SASL_SSL"));
        }
        var sitPlaintext = environment.getProperty(
                "transaction.events.mfa-assurance.sit-plaintext-enabled", Boolean.class, false);
        protocol = protocol.trim().toUpperCase(java.util.Locale.ROOT);
        if (!switch (protocol) {
            case "SSL", "SASL_SSL" -> true;
            case "PLAINTEXT", "SASL_PLAINTEXT" ->
                    environment.acceptsProfiles(Profiles.of("sit")) && sitPlaintext;
            default -> false;
        }) {
            throw new IllegalStateException(
                    "Kafka MFA assurance transport requires SSL outside SIT; plaintext requires explicit SIT opt-in");
        }
    }
}
