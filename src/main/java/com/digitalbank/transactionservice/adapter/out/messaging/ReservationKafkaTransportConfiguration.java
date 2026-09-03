package com.digitalbank.transactionservice.adapter.out.messaging;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Qualifier;
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
@ConditionalOnProperty(name = "transaction.events.reservation.enabled", havingValue = "true")
class ReservationKafkaTransportConfiguration {

    @Bean
    ReservationKafkaTransportSecurityBoundary reservationKafkaTransportSecurityBoundary(Environment environment) {
        ReservationKafkaTransportSecurityBoundary.validate(environment);
        return new ReservationKafkaTransportSecurityBoundary();
    }

    @Bean
    ProducerFactory<String, String> reservationKafkaProducerFactory(Environment environment) {
        return new DefaultKafkaProducerFactory<>(producerProperties(environment));
    }

    @Bean
    KafkaTemplate<String, String> reservationKafkaTemplate(
            @Qualifier("reservationKafkaProducerFactory") ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    ConsumerFactory<String, String> reservationKafkaConsumerFactory(Environment environment) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(environment));
    }

    @Bean(name = "reservationKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, String> reservationKafkaListenerContainerFactory(
            @Qualifier("reservationKafkaConsumerFactory") ConsumerFactory<String, String> consumerFactory,
            @Qualifier("reservationKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            Environment environment) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(reservationErrorHandler(kafkaTemplate, environment));
        return factory;
    }

    private static CommonErrorHandler reservationErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate, Environment environment) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new TopicPartition(record.topic() + ".dlq", record.partition()));
        var backOff = new ExponentialBackOffWithMaxRetries(environment.getProperty(
                "transaction.events.reservation.consumer.retry.max-attempts", Integer.class, 3));
        backOff.setInitialInterval(environment.getProperty(
                "transaction.events.reservation.consumer.retry.initial-interval-ms", Long.class, 1000L));
        backOff.setMultiplier(environment.getProperty(
                "transaction.events.reservation.consumer.retry.multiplier", Double.class, 2.0));
        backOff.setMaxInterval(environment.getProperty(
                "transaction.events.reservation.consumer.retry.max-interval-ms", Long.class, 10000L));
        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    private static Map<String, Object> producerProperties(Environment environment) {
        var properties = new HashMap<String, Object>();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, required(environment, "bootstrap-servers"));
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol(environment));
        properties.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG, "all");
        properties.put(org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        return properties;
    }

    private static Map<String, Object> consumerProperties(Environment environment) {
        var properties = new HashMap<String, Object>();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, required(environment, "bootstrap-servers"));
        properties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocol(environment));
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return properties;
    }

    private static String required(Environment environment, String suffix) {
        var value = environment.getProperty("transaction.events.reservation.kafka." + suffix);
        if (value == null || value.isBlank()) {
            value = environment.getProperty("spring.kafka." + suffix);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Kafka reservation " + suffix);
        }
        return value;
    }

    private static String protocol(Environment environment) {
        var value = environment.getProperty("transaction.events.reservation.security.protocol");
        if (value == null || value.isBlank()) {
            value = environment.getProperty("spring.kafka.properties[security.protocol]",
                    environment.getProperty("spring.kafka.properties.security.protocol", "SASL_SSL"));
        }
        return value.trim()
                .toUpperCase(java.util.Locale.ROOT);
    }
}
