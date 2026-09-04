package com.digitalbank.transactionservice.adapter.out.messaging;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableKafka
@EnableScheduling
@ConditionalOnProperty(name = "transaction.events.mfa-assurance.enabled", havingValue = "true")
class MfaAssuranceKafkaTransportConfiguration {

    @Bean
    MfaAssuranceKafkaTransportSecurityBoundary mfaAssuranceKafkaTransportSecurityBoundary(Environment environment) {
        MfaAssuranceKafkaTransportSecurityBoundary.validate(environment);
        return new MfaAssuranceKafkaTransportSecurityBoundary();
    }

    @Bean
    ProducerFactory<String, String> mfaAssuranceKafkaProducerFactory(Environment environment) {
        return new DefaultKafkaProducerFactory<>(properties(environment, true));
    }

    @Bean
    KafkaTemplate<String, String> mfaAssuranceKafkaTemplate(
            @Qualifier("mfaAssuranceKafkaProducerFactory") ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, String> mfaAssuranceKafkaConsumerFactory(Environment environment) {
        return new DefaultKafkaConsumerFactory<>(properties(environment, false));
    }

    @Bean(name = "mfaAssuranceKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> mfaAssuranceKafkaListenerContainerFactory(
            @Qualifier("mfaAssuranceKafkaConsumerFactory") ConsumerFactory<String, String> consumerFactory,
            @Qualifier("mfaAssuranceKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            Environment environment) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler(kafkaTemplate, environment));
        return factory;
    }

    private static CommonErrorHandler errorHandler(
            KafkaTemplate<String, String> kafkaTemplate, Environment environment) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new TopicPartition(record.topic() + ".dlq", record.partition()));
        var backOff = new ExponentialBackOffWithMaxRetries(environment.getProperty(
                "transaction.events.mfa-assurance.consumer.retry.max-attempts", Integer.class, 3));
        backOff.setInitialInterval(environment.getProperty(
                "transaction.events.mfa-assurance.consumer.retry.initial-interval-ms", Long.class, 1000L));
        backOff.setMultiplier(environment.getProperty(
                "transaction.events.mfa-assurance.consumer.retry.multiplier", Double.class, 2.0));
        backOff.setMaxInterval(environment.getProperty(
                "transaction.events.mfa-assurance.consumer.retry.max-interval-ms", Long.class, 10000L));
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    private static Map<String, Object> properties(Environment environment, boolean producer) {
        var properties = new HashMap<String, Object>();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, required(environment, "bootstrap-servers"));
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol(environment));
        forwardSecurityProperties(environment, properties);
        if (producer) {
            properties.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
            properties.put(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            properties.put(org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        } else {
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        }
        return properties;
    }

    private static void forwardSecurityProperties(Environment environment, Map<String, Object> properties) {
        for (var name : new String[] {
            "sasl.mechanism", "sasl.jaas.config", "ssl.truststore.location", "ssl.truststore.password",
            "ssl.truststore.type", "ssl.keystore.location", "ssl.keystore.password", "ssl.keystore.type",
            "ssl.key.password", "security.protocol"
        }) {
            var value = environment.getProperty("spring.kafka.properties[" + name + "]");
            if (value == null || value.isBlank()) {
                value = environment.getProperty("spring.kafka.properties." + name);
            }
            if (value != null && !value.isBlank()) {
                properties.put(name, value);
            }
        }
    }

    private static String required(Environment environment, String suffix) {
        var value = environment.getProperty("transaction.events.mfa-assurance.kafka." + suffix);
        if (value == null || value.isBlank()) {
            value = environment.getProperty("spring.kafka." + suffix);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Kafka MFA assurance " + suffix);
        }
        return value;
    }

    private static String protocol(Environment environment) {
        var value = environment.getProperty("transaction.events.mfa-assurance.security.protocol");
        if (value == null || value.isBlank()) {
            value = environment.getProperty("spring.kafka.properties[security.protocol]",
                    environment.getProperty("spring.kafka.properties.security.protocol", "SASL_SSL"));
        }
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
