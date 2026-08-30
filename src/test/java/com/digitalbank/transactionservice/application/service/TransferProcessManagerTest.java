package com.digitalbank.transactionservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import com.digitalbank.transactionservice.domain.TransferStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferProcessManagerTest {

    private static final UUID TRANSFER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SOURCE_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DESTINATION_ACCOUNT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CORRELATION_ID = "transfer-correlation-001";
    private static final String RESERVATION_REQUEST_ID = "reservation-request-001";
    private static final String POSTING_REQUEST_ID = "posting-request-001";

    private InMemoryWorkflowRepository workflows;
    private InMemoryEventInbox events;
    private InMemoryActionRepository actions;
    private TransferProcessManager processManager;

    @BeforeEach
    void setUp() {
        workflows = new InMemoryWorkflowRepository();
        events = new InMemoryEventInbox();
        actions = new InMemoryActionRepository();
        processManager = new TransferProcessManager(workflows, events, actions);
    }

    @Test
    void requestTransferRecordsAccountReservationAction() {
        var result = processManager.requestTransfer(command());

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().getFirst().actionId()).isEqualTo("account-reservation:" + RESERVATION_REQUEST_ID);
        assertThat(actions.actions()).hasSize(1);
    }

    @Test
    void repeatedTransferRequestIsIdempotent() {
        processManager.requestTransfer(command());

        var retry = processManager.requestTransfer(command());

        assertThat(retry.transfer().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(retry.actions()).isEmpty();
        assertThat(actions.actions()).hasSize(1);
    }

    @Test
    void conflictingTransferRequestIsRejected() {
        processManager.requestTransfer(command());

        assertThatThrownBy(() -> processManager.requestTransfer(new RequestTransferCommand(
                TRANSFER_ID,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal("99.00"),
                "AED",
                CORRELATION_ID,
                "transfer-request-001",
                RESERVATION_REQUEST_ID,
                POSTING_REQUEST_ID)))
                .isInstanceOf(TransferConflictException.class);
    }

    @Test
    void reservationCreatedRecordsLedgerPostingAction() {
        processManager.requestTransfer(command());

        var result = processManager.handle(new AccountReservationCreated(
                TRANSFER_ID, "event-reservation-001", CORRELATION_ID, RESERVATION_REQUEST_ID, "reservation-001"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().getFirst().actionId()).isEqualTo("ledger-posting:" + POSTING_REQUEST_ID);
    }

    @Test
    void reservationRejectedFailsTransferWithoutReleaseAction() {
        processManager.requestTransfer(command());

        var result = processManager.handle(new AccountReservationRejected(
                TRANSFER_ID, "event-reservation-002", CORRELATION_ID, RESERVATION_REQUEST_ID, "insufficient funds"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void ledgerCompletionCompletesTransfer() {
        reserve();

        var result = processManager.handle(new LedgerPostingCompleted(
                TRANSFER_ID, "event-ledger-001", CORRELATION_ID, "ledger-event-request-001", POSTING_REQUEST_ID));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void ledgerFailureFailsTransferAndReleasesReservation() {
        reserve();

        var result = processManager.handle(new LedgerPostingFailed(
                TRANSFER_ID,
                "event-ledger-002",
                CORRELATION_ID,
                "ledger-event-request-002",
                POSTING_REQUEST_ID,
                "unbalanced posting"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.actions()).hasSize(1);
        assertThat(result.actions().getFirst().actionId()).isEqualTo("release-reservation:reservation-001");
    }

    @Test
    void duplicateEventIdDoesNotRepeatTransitionOrAction() {
        reserve();
        var event = new LedgerPostingCompleted(
                TRANSFER_ID, "event-ledger-003", CORRELATION_ID, "ledger-event-request-003", POSTING_REQUEST_ID);

        processManager.handle(event);
        var retry = processManager.handle(event);

        assertThat(retry.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(retry.actions()).isEmpty();
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void duplicateReservationEventWithNewEventIdDoesNotRepeatLedgerAction() {
        reserve();

        var retry = processManager.handle(new AccountReservationCreated(
                TRANSFER_ID, "event-reservation-retry", CORRELATION_ID, RESERVATION_REQUEST_ID, "reservation-001"));

        assertThat(retry.transfer().status()).isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(retry.actions()).isEmpty();
        assertThat(actions.actions()).hasSize(2);
    }

    @Test
    void outOfOrderLedgerCompletionIsDeferredAndReplayedAfterReservation() {
        processManager.requestTransfer(command());

        processManager.handle(new LedgerPostingCompleted(
                TRANSFER_ID, "event-ledger-004", CORRELATION_ID, "ledger-event-request-004", POSTING_REQUEST_ID));

        var result = processManager.handle(new AccountReservationCreated(
                TRANSFER_ID, "event-reservation-003", CORRELATION_ID, RESERVATION_REQUEST_ID, "reservation-001"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(events.deferredEventIds()).isEmpty();
        assertThat(actions.actions()).hasSize(1);
    }

    @Test
    void correlationMismatchIsRejectedBeforeInboxWrite() {
        processManager.requestTransfer(command());

        assertThatThrownBy(() -> processManager.handle(new AccountReservationCreated(
                TRANSFER_ID, "event-reservation-004", "wrong-correlation", RESERVATION_REQUEST_ID, "reservation-001")))
                .isInstanceOf(TransferConflictException.class);
        assertThat(events.eventIds()).isEmpty();
    }

    private void reserve() {
        processManager.requestTransfer(command());
        processManager.handle(new AccountReservationCreated(
                TRANSFER_ID, "event-reservation-005", CORRELATION_ID, RESERVATION_REQUEST_ID, "reservation-001"));
    }

    private static RequestTransferCommand command() {
        return new RequestTransferCommand(
                TRANSFER_ID,
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal("12.50"),
                "AED",
                CORRELATION_ID,
                "transfer-request-001",
                RESERVATION_REQUEST_ID,
                POSTING_REQUEST_ID);
    }

    private static final class InMemoryWorkflowRepository implements TransferWorkflowRepository {
        private final Map<UUID, Transfer> values = new HashMap<>();

        @Override
        public Optional<Transfer> findById(UUID transferId) {
            return Optional.ofNullable(values.get(transferId));
        }

        @Override
        public Transfer save(Transfer transfer) {
            values.put(transfer.id(), transfer);
            return transfer;
        }
    }

    private static final class InMemoryEventInbox implements WorkflowEventInbox {
        private final Map<String, WorkflowEventRecord> values = new HashMap<>();

        @Override
        public Optional<WorkflowEventRecord> findByEventId(String eventId) {
            return Optional.ofNullable(values.get(eventId));
        }

        @Override
        public void defer(WorkflowEventRecord event) {
            values.put(event.eventId(), event.deferred());
        }

        @Override
        public void markProcessed(String eventId) {
            values.computeIfPresent(eventId, (ignored, event) -> event.processed());
        }

        @Override
        public void recordProcessed(WorkflowEventRecord event) {
            values.put(event.eventId(), event.processed());
        }

        @Override
        public List<WorkflowEventRecord> findDeferredByTransferId(UUID transferId) {
            return values.values().stream()
                    .filter(event -> event.transferId().equals(transferId) && event.isDeferred())
                    .toList();
        }

        Set<String> eventIds() {
            return new HashSet<>(values.keySet());
        }

        Set<String> deferredEventIds() {
            return values.values().stream()
                    .filter(WorkflowEventRecord::isDeferred)
                    .map(WorkflowEventRecord::eventId)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static final class InMemoryActionRepository implements WorkflowActionRepository {
        private final List<WorkflowAction> values = new ArrayList<>();
        private final Set<String> ids = new HashSet<>();

        @Override
        public boolean recordIfAbsent(WorkflowAction action) {
            if (!ids.add(action.actionId())) {
                return false;
            }
            values.add(action);
            return true;
        }

        @Override
        public long countByTransferId(UUID transferId) {
            return values.stream().filter(action -> action.transferId().equals(transferId)).count();
        }

        List<WorkflowAction> actions() {
            return values;
        }
    }
}
