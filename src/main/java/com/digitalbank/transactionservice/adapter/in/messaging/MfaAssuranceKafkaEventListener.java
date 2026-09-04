package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.port.in.MfaAssuranceGranted;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.mfa-assurance.enabled", havingValue = "true")
class MfaAssuranceKafkaEventListener {

    private final ObjectMapper objectMapper;
    private final MfaAssuranceEventTarget processManager;
    private final String topic;

    @Autowired
    MfaAssuranceKafkaEventListener(ObjectMapper objectMapper, TransferProcessManager processManager, Environment environment) {
        this(objectMapper, new ProcessManagerTarget(processManager),
                environment.getRequiredProperty("transaction.events.mfa-assurance.topic"));
    }

    MfaAssuranceKafkaEventListener(ObjectMapper objectMapper, MfaAssuranceEventTarget processManager) {
        this(objectMapper, processManager, "mfa.assurance.granted.v1");
    }

    private MfaAssuranceKafkaEventListener(
            ObjectMapper objectMapper, MfaAssuranceEventTarget processManager, String topic) {
        this.objectMapper = objectMapper;
        this.processManager = processManager;
        this.topic = topic;
    }

    @KafkaListener(
            topics = "${transaction.events.mfa-assurance.topic}",
            groupId = "${transaction.events.mfa-assurance.consumer.group-id}",
            containerFactory = "mfaAssuranceKafkaListenerContainerFactory")
    void onMfaAssuranceEvent(ConsumerRecord<String, String> record) {
        if (!topic.equals(record.topic())) {
            throw new MfaAssuranceEventValidationException("Unsupported MFA assurance Kafka topic");
        }
        processManager.handle(MfaAssuranceEventPayloads.granted(record, objectMapper));
    }

    @FunctionalInterface
    interface MfaAssuranceEventTarget {
        void handle(MfaAssuranceGranted event);
    }

    private static final class ProcessManagerTarget implements MfaAssuranceEventTarget {
        private final TransferProcessManager processManager;

        private ProcessManagerTarget(TransferProcessManager processManager) {
            this.processManager = processManager;
        }

        @Override
        public void handle(MfaAssuranceGranted event) {
            processManager.handle(event);
        }
    }
}
