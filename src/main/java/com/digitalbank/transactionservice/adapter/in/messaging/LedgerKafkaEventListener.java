package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.ledger.enabled", havingValue = "true")
class LedgerKafkaEventListener {

    private final ObjectMapper objectMapper;
    private final LedgerEventTarget processManager;
    private final String completedTopic;
    private final String failedTopic;

    LedgerKafkaEventListener(ObjectMapper objectMapper, TransferProcessManager processManager, Environment environment) {
        this(
                objectMapper,
                new ProcessManagerTarget(processManager),
                environment.getRequiredProperty("transaction.events.ledger.completed-topic"),
                environment.getRequiredProperty("transaction.events.ledger.failed-topic"));
    }

    LedgerKafkaEventListener(
            ObjectMapper objectMapper,
            LedgerEventTarget processManager,
            String completedTopic,
            String failedTopic) {
        this.objectMapper = objectMapper;
        this.processManager = processManager;
        this.completedTopic = completedTopic;
        this.failedTopic = failedTopic;
    }

    @KafkaListener(
            topics = {
                "${transaction.events.ledger.completed-topic}",
                "${transaction.events.ledger.failed-topic}"
            },
            groupId = "${transaction.events.ledger.consumer.group-id}",
            containerFactory = "ledgerKafkaListenerContainerFactory")
    void onLedgerEvent(ConsumerRecord<String, String> record) {
        if (completedTopic.equals(record.topic())) {
            processManager.handle(LedgerEventPayloads.completed(record, objectMapper));
            return;
        }
        if (failedTopic.equals(record.topic())) {
            processManager.handle(LedgerEventPayloads.failed(record, objectMapper));
            return;
        }
        throw new LedgerEventValidationException("Unsupported ledger Kafka topic");
    }

    @FunctionalInterface
    interface LedgerEventTarget {
        void handle(Object event);
    }

    private static final class ProcessManagerTarget implements LedgerEventTarget {
        private final TransferProcessManager processManager;

        private ProcessManagerTarget(TransferProcessManager processManager) {
            this.processManager = processManager;
        }

        @Override
        public void handle(Object event) {
            if (event instanceof LedgerPostingCompleted completed) {
                processManager.handle(completed);
            } else if (event instanceof LedgerPostingFailed failed) {
                processManager.handle(failed);
            } else {
                throw new IllegalArgumentException("Unsupported ledger event: " + event.getClass());
            }
        }
    }
}
