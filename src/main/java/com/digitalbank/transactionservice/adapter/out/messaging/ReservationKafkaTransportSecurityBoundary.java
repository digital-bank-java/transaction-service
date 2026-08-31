package com.digitalbank.transactionservice.adapter.out.messaging;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

final class ReservationKafkaTransportSecurityBoundary {

    ReservationKafkaTransportSecurityBoundary() {}

    static void validate(Environment environment) {
        var protocol = environment
                .getProperty("transaction.events.reservation.security.protocol", "SSL")
                .trim()
                .toUpperCase(java.util.Locale.ROOT);
        if (!switch (protocol) {
            case "SSL", "SASL_SSL" -> true;
            case "PLAINTEXT", "SASL_PLAINTEXT" ->
                    environment.acceptsProfiles(Profiles.of("sit"))
                            && environment.getProperty(
                                    "transaction.events.reservation.sit-plaintext-enabled", Boolean.class, false);
            default -> false;
        }) {
            throw new IllegalStateException(
                    "Kafka reservation transport requires SSL outside SIT; plaintext requires explicit SIT opt-in");
        }
    }
}
