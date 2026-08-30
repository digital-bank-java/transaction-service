package com.digitalbank.transactionservice.application.service;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.ReleaseAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestLedgerPosting;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventType;
import com.digitalbank.transactionservice.domain.IllegalTransferTransitionException;
import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import com.digitalbank.transactionservice.domain.TransferStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferProcessManager {

    private final TransferWorkflowRepository workflowRepository;
    private final WorkflowEventInbox eventInbox;
    private final WorkflowActionRepository actionRepository;

    public TransferProcessManager(
            TransferWorkflowRepository workflowRepository,
            WorkflowEventInbox eventInbox,
            WorkflowActionRepository actionRepository) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.eventInbox = Objects.requireNonNull(eventInbox, "eventInbox must not be null");
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository must not be null");
    }

    @Transactional
    public WorkflowResult requestTransfer(RequestTransferCommand command) {
        var existing = workflowRepository.findById(command.transferId());
        if (existing.isPresent()) {
            assertSameRequest(existing.orElseThrow(), command);
            return result(existing.orElseThrow());
        }

        var transfer = Transfer.request(
                command.transferId(),
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amount(),
                command.currency(),
                command.correlationId(),
                command.transferRequestId(),
                command.reservationRequestId(),
                command.postingRequestId());
        workflowRepository.save(transfer);

        var action = RequestAccountReservation.forTransfer(transfer);
        return result(transfer, record(action));
    }

    @Transactional
    public WorkflowResult handle(AccountReservationCreated event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(AccountReservationRejected event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(LedgerPostingCompleted event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(LedgerPostingFailed event) {
        return handle(WorkflowEventRecord.from(event));
    }

    private WorkflowResult handle(WorkflowEventRecord event) {
        var transfer = workflowRepository.findById(event.transferId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + event.transferId()));
        requireCorrelation(transfer, event.correlationId());
        requireEventIdentifiers(transfer, event);

        var previous = eventInbox.findByEventId(event.eventId());
        if (previous.isPresent()) {
            if (!sameEvent(previous.orElseThrow(), event)) {
                throw new TransferConflictException("eventId already exists with different payload");
            }
            return result(transfer);
        }

        if (isOutOfOrderLedgerEvent(transfer, event)) {
            eventInbox.defer(event);
            return result(transfer);
        }

        var actions = new ArrayList<WorkflowAction>();
        switch (event.eventType()) {
            case ACCOUNT_RESERVATION_CREATED -> applyReservationCreated(transfer, event, actions);
            case ACCOUNT_RESERVATION_REJECTED -> applyReservationRejected(transfer, event);
            case LEDGER_POSTING_COMPLETED -> applyLedgerCompleted(transfer, event);
            case LEDGER_POSTING_FAILED -> applyLedgerFailed(transfer, event, actions);
        }
        return result(transfer, actions);
    }

    private void applyReservationCreated(Transfer transfer, WorkflowEventRecord event, List<WorkflowAction> actions) {
        var changed = transfer.accountReservationCreated(
                event.reservationRequestId(), event.reservationId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
            var deferredLedgerEvents = eventInbox.findDeferredByTransferId(transfer.id());
            if (deferredLedgerEvents.isEmpty()) {
                addAction(actions, RequestLedgerPosting.forTransfer(transfer));
            }
            eventInbox.recordProcessed(event);
            replayDeferredLedgerEvents(transfer, deferredLedgerEvents, actions);
        } else {
            eventInbox.recordProcessed(event);
        }
    }

    private void applyReservationRejected(Transfer transfer, WorkflowEventRecord event) {
        var changed = transfer.accountReservationRejected();
        if (changed) {
            workflowRepository.save(transfer);
        }
        eventInbox.recordProcessed(event);
    }

    private void applyLedgerCompleted(Transfer transfer, WorkflowEventRecord event) {
        var changed = transfer.ledgerPostingCompleted(event.postingRequestId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
        }
        eventInbox.recordProcessed(event);
    }

    private void applyLedgerFailed(Transfer transfer, WorkflowEventRecord event, List<WorkflowAction> actions) {
        var changed = transfer.ledgerPostingFailed(event.postingRequestId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
            addAction(actions, ReleaseAccountReservation.forTransfer(
                    transfer.id(), transfer.correlationId(), transfer.reservationId()));
        }
        eventInbox.recordProcessed(event);
    }

    private void replayDeferredLedgerEvents(
            Transfer transfer, List<WorkflowEventRecord> deferredEvents, List<WorkflowAction> actions) {
        for (var deferred : deferredEvents) {
            if (deferred.eventType() == EventType.LEDGER_POSTING_COMPLETED) {
                applyLedgerCompleted(transfer, deferred);
            } else if (deferred.eventType() == EventType.LEDGER_POSTING_FAILED) {
                applyLedgerFailed(transfer, deferred, actions);
            }
        }
    }

    private boolean isOutOfOrderLedgerEvent(Transfer transfer, WorkflowEventRecord event) {
        return transfer.status() == TransferStatus.PENDING
                && (event.eventType() == EventType.LEDGER_POSTING_COMPLETED
                        || event.eventType() == EventType.LEDGER_POSTING_FAILED);
    }

    private void requireEventIdentifiers(Transfer transfer, WorkflowEventRecord event) {
        if (event.eventType() == EventType.ACCOUNT_RESERVATION_CREATED
                || event.eventType() == EventType.ACCOUNT_RESERVATION_REJECTED) {
            requireMatch(transfer.reservationRequestId(), event.reservationRequestId(), "reservationRequestId");
        }
        if (event.eventType() == EventType.LEDGER_POSTING_COMPLETED
                || event.eventType() == EventType.LEDGER_POSTING_FAILED) {
            requireMatch(transfer.postingRequestId(), event.postingRequestId(), "postingRequestId");
        }
    }

    private static void requireCorrelation(Transfer transfer, String correlationId) {
        requireMatch(transfer.correlationId(), correlationId, "correlationId");
    }

    private static void requireMatch(String expected, String actual, String field) {
        if (!Objects.equals(expected, actual)) {
            throw new TransferConflictException(field + " does not match transfer workflow");
        }
    }

    private static boolean sameEvent(WorkflowEventRecord previous, WorkflowEventRecord current) {
        return previous.transferId().equals(current.transferId())
                && previous.eventType() == current.eventType()
                && previous.correlationId().equals(current.correlationId())
                && Objects.equals(previous.requestId(), current.requestId())
                && Objects.equals(previous.reservationRequestId(), current.reservationRequestId())
                && Objects.equals(previous.reservationId(), current.reservationId())
                && Objects.equals(previous.postingRequestId(), current.postingRequestId())
                && Objects.equals(previous.reason(), current.reason());
    }

    private void assertSameRequest(Transfer transfer, RequestTransferCommand command) {
        if (!transfer.id().equals(command.transferId())
                || !transfer.sourceAccountId().equals(command.sourceAccountId())
                || !transfer.destinationAccountId().equals(command.destinationAccountId())
                || transfer.amount().compareTo(command.amount()) != 0
                || !transfer.currency().equals(command.currency())
                || !transfer.correlationId().equals(command.correlationId())
                || !transfer.transferRequestId().equals(command.transferRequestId())
                || !transfer.reservationRequestId().equals(command.reservationRequestId())
                || !transfer.postingRequestId().equals(command.postingRequestId())) {
            throw new TransferConflictException("transfer request conflicts with existing workflow");
        }
    }

    private List<WorkflowAction> record(WorkflowAction action) {
        return actionRepository.recordIfAbsent(action) ? List.of(action) : List.of();
    }

    private void addAction(List<WorkflowAction> actions, WorkflowAction action) {
        if (actionRepository.recordIfAbsent(action)) {
            actions.add(action);
        }
    }

    private static WorkflowResult result(Transfer transfer) {
        return new WorkflowResult(transfer, List.of());
    }

    private static WorkflowResult result(Transfer transfer, List<WorkflowAction> actions) {
        return new WorkflowResult(transfer, actions);
    }
}
