package com.digitalbank.transactionservice.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class KafkaListenerSpringConstructionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ListenerConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(TransferProcessManager.class, KafkaListenerSpringConstructionTest::transferProcessManager)
            .withPropertyValues(
                    "transaction.events.reservation.enabled=true",
                    "transaction.events.ledger.enabled=true",
                    "transaction.events.reservation.accepted-topic=account.reservation.accepted.v1",
                    "transaction.events.reservation.rejected-topic=account.reservation.rejected.v1",
                    "transaction.events.reservation.released-topic=account.reservation.released.v1",
                    "transaction.events.reservation.expired-topic=account.reservation.expired.v1",
                    "transaction.events.ledger.completed-topic=ledger.posting.completed.v1",
                    "transaction.events.ledger.failed-topic=ledger.posting.failed.v1");

    @Test
    void createsReservationAndLedgerListenersWhenBothTransportsAreEnabled() {
        contextRunner.run(context -> assertThat(context)
                .hasSingleBean(ReservationKafkaEventListener.class)
                .hasSingleBean(LedgerKafkaEventListener.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({ReservationKafkaEventListener.class, LedgerKafkaEventListener.class})
    static class ListenerConfiguration {}

    private static TransferProcessManager transferProcessManager() {
        return new TransferProcessManager(
                new StubTransferWorkflowRepository(),
                new StubWorkflowEventInbox(),
                new StubWorkflowActionRepository(),
                event -> false,
                new StubReservationCommandEventOutbox(),
                new StubLedgerCommandEventOutbox());
    }

    private static final class StubTransferWorkflowRepository implements TransferWorkflowRepository {
        @Override
        public Optional<com.digitalbank.transactionservice.domain.Transfer> findById(UUID transferId) {
            return Optional.empty();
        }

        @Override
        public com.digitalbank.transactionservice.domain.Transfer saveIfAbsent(
                com.digitalbank.transactionservice.domain.Transfer transfer) {
            return transfer;
        }

        @Override
        public com.digitalbank.transactionservice.domain.Transfer save(
                com.digitalbank.transactionservice.domain.Transfer transfer) {
            return transfer;
        }
    }

    private static final class StubWorkflowEventInbox implements WorkflowEventInbox {
        @Override
        public Optional<WorkflowEventRecord> findByEventId(String eventId) {
            return Optional.empty();
        }

        @Override
        public void defer(WorkflowEventRecord event) {}

        @Override
        public void recordProcessed(WorkflowEventRecord event) {}

        @Override
        public void markProcessed(String eventId) {}

        @Override
        public List<WorkflowEventRecord> findDeferredByTransferId(UUID transferId) {
            return List.of();
        }
    }

    private static final class StubWorkflowActionRepository implements WorkflowActionRepository {
        @Override
        public boolean recordIfAbsent(WorkflowAction action) {
            return false;
        }

        @Override
        public long countByTransferId(UUID transferId) {
            return 0;
        }
    }

    private static final class StubReservationCommandEventOutbox implements ReservationCommandEventOutbox {
        @Override
        public boolean recordIfAbsent(ReservationCommandEvent event) {
            return false;
        }

        @Override
        public List<ReservationCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(ReservationCommandEvent event, UUID claimToken, Instant publishedAt) {}

        @Override
        public void markFailed(ReservationCommandEvent event, UUID claimToken, String error, Instant retryAt) {}
    }

    private static final class StubLedgerCommandEventOutbox implements LedgerCommandEventOutbox {
        @Override
        public boolean recordIfAbsent(LedgerCommandEvent event) {
            return false;
        }

        @Override
        public List<LedgerCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(LedgerCommandEvent event, UUID claimToken, Instant publishedAt) {}

        @Override
        public void markFailed(LedgerCommandEvent event, UUID claimToken, String error, Instant retryAt) {}
    }
}
