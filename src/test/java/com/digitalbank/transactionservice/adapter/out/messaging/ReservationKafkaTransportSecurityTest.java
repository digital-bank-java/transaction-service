package com.digitalbank.transactionservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ReservationKafkaTransportSecurityTest {

    @Test
    void rejectsPlaintextUnlessSitAndExplicitOptInAreBothPresent() {
        var environment = new MockEnvironment()
                .withProperty("transaction.events.reservation.security.protocol", "PLAINTEXT")
                .withProperty("transaction.events.reservation.sit-plaintext-enabled", "false");

        assertThatThrownBy(() -> ReservationKafkaTransportSecurityBoundary.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SIT");
    }

    @Test
    void permitsExplicitSitPlaintext() {
        var environment = new MockEnvironment()
                .withProperty("transaction.events.reservation.security.protocol", "PLAINTEXT")
                .withProperty("transaction.events.reservation.sit-plaintext-enabled", "true")
                .withProperty("spring.profiles.active", "sit");
        environment.setActiveProfiles("sit");

        assertThatCode(() -> ReservationKafkaTransportSecurityBoundary.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void defaultsToTls() {
        var environment = new MockEnvironment();

        assertThatCode(() -> ReservationKafkaTransportSecurityBoundary.validate(environment))
                .doesNotThrowAnyException();
    }
}
