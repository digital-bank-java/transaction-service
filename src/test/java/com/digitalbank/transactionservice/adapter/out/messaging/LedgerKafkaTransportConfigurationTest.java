package com.digitalbank.transactionservice.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.mock.env.MockEnvironment;

class LedgerKafkaTransportConfigurationTest {

    @Test
    void producerFactoryUsesSharedKafkaConnectionSettings() {
        var environment = new MockEnvironment()
                .withProperty("spring.kafka.bootstrap-servers", "broker:9092")
                .withProperty("spring.kafka.properties[security.protocol]", "SASL_SSL");

        var factory = new LedgerKafkaTransportConfiguration().ledgerKafkaProducerFactory(environment);

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        assertThat(factory.getConfigurationProperties())
                .containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "broker:9092")
                .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL");
    }
}
