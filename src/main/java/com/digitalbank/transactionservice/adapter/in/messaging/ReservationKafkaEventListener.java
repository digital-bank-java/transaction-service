package com.digitalbank.transactionservice.adapter.in.messaging;

import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "transaction.events.reservation.enabled", havingValue = "true")
class ReservationKafkaEventListener {

    private final ObjectMapper objectMapper;
    private final ReservationEventTarget processManager;

    ReservationKafkaEventListener(ObjectMapper objectMapper, TransferProcessManager processManager) {
        this(objectMapper, new ProcessManagerTarget(processManager));
    }

    ReservationKafkaEventListener(ObjectMapper objectMapper, ReservationEventTarget processManager) {
        this.objectMapper = objectMapper;
        this.processManager = processManager;
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
        switch (ReservationEventPayloads.eventType(record, objectMapper)) {
            case "AccountReservationAccepted.v1" -> processManager.handle(
                    ReservationEventPayloads.accepted(record, objectMapper));
            case "AccountReservationRejected.v1" -> processManager.handle(
                    ReservationEventPayloads.rejected(record, objectMapper));
            case "AccountReservationReleased.v1" -> processManager.handle(
                    ReservationEventPayloads.released(record, objectMapper));
            case "AccountReservationExpired.v1" -> processManager.handle(
                    ReservationEventPayloads.expired(record, objectMapper));
            default -> throw new ReservationEventValidationException("Unsupported reservation Kafka topic");
        }
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
