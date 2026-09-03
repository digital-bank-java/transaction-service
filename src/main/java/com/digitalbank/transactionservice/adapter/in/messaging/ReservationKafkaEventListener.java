package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.reservation.enabled", havingValue = "true")
class ReservationKafkaEventListener {

    private final ObjectMapper objectMapper;
    private final ReservationEventTarget processManager;
    private final String acceptedTopic;
    private final String rejectedTopic;
    private final String releasedTopic;
    private final String expiredTopic;

    @Autowired
    ReservationKafkaEventListener(ObjectMapper objectMapper, TransferProcessManager processManager, Environment environment) {
        this(
                objectMapper,
                new ProcessManagerTarget(processManager),
                environment.getRequiredProperty("transaction.events.reservation.accepted-topic"),
                environment.getRequiredProperty("transaction.events.reservation.rejected-topic"),
                environment.getRequiredProperty("transaction.events.reservation.released-topic"),
                environment.getRequiredProperty("transaction.events.reservation.expired-topic"));
    }

    ReservationKafkaEventListener(ObjectMapper objectMapper, ReservationEventTarget processManager) {
        this(
                objectMapper,
                processManager,
                "account.reservation.accepted.v1",
                "account.reservation.rejected.v1",
                "account.reservation.released.v1",
                "account.reservation.expired.v1");
    }

    private ReservationKafkaEventListener(
            ObjectMapper objectMapper,
            ReservationEventTarget processManager,
            String acceptedTopic,
            String rejectedTopic,
            String releasedTopic,
            String expiredTopic) {
        this.objectMapper = objectMapper;
        this.processManager = processManager;
        this.acceptedTopic = acceptedTopic;
        this.rejectedTopic = rejectedTopic;
        this.releasedTopic = releasedTopic;
        this.expiredTopic = expiredTopic;
    }

    @KafkaListener(
            topics = {
                "${transaction.events.reservation.accepted-topic}",
                "${transaction.events.reservation.rejected-topic}",
                "${transaction.events.reservation.released-topic}",
                "${transaction.events.reservation.expired-topic}"
            },
            groupId = "${transaction.events.reservation.consumer.group-id}",
            containerFactory = "reservationKafkaListenerContainerFactory")
    void onReservationEvent(ConsumerRecord<String, String> record) {
        if (acceptedTopic.equals(record.topic())) {
            processManager.handle(ReservationEventPayloads.accepted(record, objectMapper));
            return;
        }
        if (rejectedTopic.equals(record.topic())) {
            processManager.handle(ReservationEventPayloads.rejected(record, objectMapper));
            return;
        }
        if (releasedTopic.equals(record.topic())) {
            processManager.handle(ReservationEventPayloads.released(record, objectMapper));
            return;
        }
        if (expiredTopic.equals(record.topic())) {
            processManager.handle(ReservationEventPayloads.expired(record, objectMapper));
            return;
        }
        throw new ReservationEventValidationException("Unsupported reservation Kafka topic");
    }

    @FunctionalInterface
    interface ReservationEventTarget {
        void handle(Object event);
    }

    private static final class ProcessManagerTarget implements ReservationEventTarget {
        private final TransferProcessManager processManager;

        private ProcessManagerTarget(TransferProcessManager processManager) {
            this.processManager = processManager;
        }

        @Override
        public void handle(Object event) {
            if (event instanceof AccountReservationAccepted accepted) {
                processManager.handle(accepted);
            } else if (event instanceof AccountReservationRejected rejected) {
                processManager.handle(rejected);
            } else if (event instanceof AccountReservationReleased released) {
                processManager.handle(released);
            } else if (event instanceof AccountReservationExpired expired) {
                processManager.handle(expired);
            } else {
                throw new IllegalArgumentException("Unsupported reservation event: " + event.getClass());
            }
        }
    }
}
