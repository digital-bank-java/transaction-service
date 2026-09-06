package com.digitalbank.transactionservice.application.service;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.digitalbank.transactionservice.application.port.in.MfaAssuranceGranted;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.ReleaseAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestAccountReservation;
import com.digitalbank.transactionservice.application.port.out.RequestLedgerPosting;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEventOutbox;
import com.digitalbank.transactionservice.application.port.out.TransferCompletedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferFailedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEventOutbox;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.LedgerPostingRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.AccountReservationReleaseRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.AccountReservationRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventType;
import com.digitalbank.transactionservice.domain.IllegalTransferTransitionException;
import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import com.digitalbank.transactionservice.domain.TransferStatus;
import com.digitalbank.transactionservice.risk.ConfiguredTransferRiskEvaluator;
import com.digitalbank.transactionservice.risk.TransferRiskDecision;
import com.digitalbank.transactionservice.risk.TransferRiskEvaluator;
import com.digitalbank.transactionservice.risk.TransferRiskIntent;
import com.digitalbank.transactionservice.risk.TransferRiskProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferProcessManager {

    private final TransferWorkflowRepository workflowRepository;
    private final WorkflowEventInbox eventInbox;
    private final WorkflowActionRepository actionRepository;
    private final TransferCreatedEventOutbox transferCreatedEventOutbox;
    private final TransferTerminalEventOutbox transferTerminalEventOutbox;
    private final ReservationCommandEventOutbox reservationCommandEventOutbox;
    private final LedgerCommandEventOutbox ledgerCommandEventOutbox;
    private final TransferRiskEvaluator riskEvaluator;

    public TransferProcessManager(
            TransferWorkflowRepository workflowRepository,
            WorkflowEventInbox eventInbox,
            WorkflowActionRepository actionRepository,
            TransferCreatedEventOutbox transferCreatedEventOutbox) {
        this(workflowRepository, eventInbox, actionRepository, transferCreatedEventOutbox,
                new NoOpReservationCommandEventOutbox(), new NoOpLedgerCommandEventOutbox(),
                new NoOpTransferTerminalEventOutbox(), legacyRiskEvaluator());
    }

    public TransferProcessManager(
            TransferWorkflowRepository workflowRepository,
            WorkflowEventInbox eventInbox,
            WorkflowActionRepository actionRepository,
            TransferCreatedEventOutbox transferCreatedEventOutbox,
            ReservationCommandEventOutbox reservationCommandEventOutbox,
            LedgerCommandEventOutbox ledgerCommandEventOutbox) {
        this(workflowRepository, eventInbox, actionRepository, transferCreatedEventOutbox,
                reservationCommandEventOutbox, ledgerCommandEventOutbox,
                new NoOpTransferTerminalEventOutbox(), legacyRiskEvaluator());
    }

    public TransferProcessManager(
            TransferWorkflowRepository workflowRepository,
            WorkflowEventInbox eventInbox,
            WorkflowActionRepository actionRepository,
            TransferCreatedEventOutbox transferCreatedEventOutbox,
            ReservationCommandEventOutbox reservationCommandEventOutbox,
            LedgerCommandEventOutbox ledgerCommandEventOutbox,
            TransferRiskEvaluator riskEvaluator) {
        this(workflowRepository, eventInbox, actionRepository, transferCreatedEventOutbox,
                reservationCommandEventOutbox, ledgerCommandEventOutbox,
                new NoOpTransferTerminalEventOutbox(), riskEvaluator);
    }

    @Autowired
    public TransferProcessManager(
            TransferWorkflowRepository workflowRepository,
            WorkflowEventInbox eventInbox,
            WorkflowActionRepository actionRepository,
            TransferCreatedEventOutbox transferCreatedEventOutbox,
            ReservationCommandEventOutbox reservationCommandEventOutbox,
            LedgerCommandEventOutbox ledgerCommandEventOutbox,
            TransferTerminalEventOutbox transferTerminalEventOutbox,
            TransferRiskEvaluator riskEvaluator) {
        this.workflowRepository = Objects.requireNonNull(workflowRepository, "workflowRepository must not be null");
        this.eventInbox = Objects.requireNonNull(eventInbox, "eventInbox must not be null");
        this.actionRepository = Objects.requireNonNull(actionRepository, "actionRepository must not be null");
        this.transferCreatedEventOutbox = Objects.requireNonNull(
                transferCreatedEventOutbox, "transferCreatedEventOutbox must not be null");
        this.transferTerminalEventOutbox = Objects.requireNonNull(
                transferTerminalEventOutbox, "transferTerminalEventOutbox must not be null");
        this.reservationCommandEventOutbox = Objects.requireNonNull(
                reservationCommandEventOutbox, "reservationCommandEventOutbox must not be null");
        this.ledgerCommandEventOutbox = Objects.requireNonNull(
                ledgerCommandEventOutbox, "ledgerCommandEventOutbox must not be null");
        this.riskEvaluator = Objects.requireNonNull(riskEvaluator, "riskEvaluator must not be null");
    }

    @Transactional
    public WorkflowResult requestTransfer(RequestTransferCommand command) {
        var riskDecision = riskEvaluator.evaluate(
                new TransferRiskIntent(
                        command.transferId(),
                        command.decisionRequestId(),
                        command.customerId(),
                        command.sourceAccountId(),
                        command.destinationAccountId(),
                        command.amount(),
                        command.currency(),
                        command.channel(),
                        command.destinationClass(),
                        command.correlationId()),
                java.time.Instant.now());
        var transfer = Transfer.request(
                command.transferId(),
                command.sourceAccountId(),
                command.destinationAccountId(),
                command.amount(),
                command.currency(),
                command.customerId(),
                command.channel(),
                command.destinationClass(),
                command.correlationId(),
                command.transferRequestId(),
                command.reservationRequestId(),
                command.postingRequestId(),
                riskDecision);
        if (riskDecision.outcome() == com.digitalbank.transactionservice.risk.TransferRiskOutcome.DECLINE) {
            transfer.declineForRisk();
        }
        var persisted = workflowRepository.saveIfAbsent(transfer);
        assertSameRequest(persisted, command);

        var created = transferCreatedEventOutbox.recordIfAbsent(TransferCreatedEvent.from(persisted));
        if (!persisted.riskAllowsReservation(java.time.Instant.now())) {
            return result(persisted, List.of(), created);
        }
        var action = RequestAccountReservation.forTransfer(persisted);
        return result(persisted, record(persisted, action), created);
    }

    @Transactional(readOnly = true)
    public Optional<WorkflowResult> findTransferWorkflow(UUID transferId) {
        Objects.requireNonNull(transferId, "transferId must not be null");
        return workflowRepository.findById(transferId).map(TransferProcessManager::result);
    }

    @Transactional
    public WorkflowResult handle(AccountReservationCreated event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(AccountReservationAccepted event) {
        var record = WorkflowEventRecord.from(event);
        return handle(record, transfer -> requireReservationAcceptedMatches(transfer, record));
    }

    @Transactional
    public WorkflowResult handle(AccountReservationRejected event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(AccountReservationReleased event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(AccountReservationExpired event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(LedgerPostingCompleted event) {
        return handle(WorkflowEventRecord.from(event), transfer -> requireLedgerCompletionMatches(transfer, event));
    }

    @Transactional
    public WorkflowResult handle(LedgerPostingFailed event) {
        return handle(WorkflowEventRecord.from(event));
    }

    @Transactional
    public WorkflowResult handle(MfaAssuranceGranted event) {
        return handle(WorkflowEventRecord.from(event));
    }

    private WorkflowResult handle(WorkflowEventRecord event) {
        return handle(event, ignored -> {});
    }

    private WorkflowResult handle(WorkflowEventRecord event, Consumer<Transfer> eventValidator) {
        var transfer = workflowRepository.findById(event.transferId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + event.transferId()));
        requireCorrelation(transfer, event.correlationId());
        requireEventIdentifiers(transfer, event);
        eventValidator.accept(transfer);

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
            case ACCOUNT_RESERVATION_CREATED, ACCOUNT_RESERVATION_ACCEPTED -> applyReservationCreated(transfer, event, actions);
            case ACCOUNT_RESERVATION_REJECTED -> applyReservationRejected(transfer, event);
            case ACCOUNT_RESERVATION_RELEASED -> applyReservationReleased(transfer, event);
            case ACCOUNT_RESERVATION_EXPIRED -> applyReservationExpired(transfer, event);
            case LEDGER_POSTING_COMPLETED -> applyLedgerCompleted(transfer, event);
            case LEDGER_POSTING_FAILED -> applyLedgerFailed(transfer, event, actions);
            case MFA_ASSURANCE_GRANTED -> applyMfaAssurance(transfer, event, actions);
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
                addAction(actions, transfer, RequestLedgerPosting.forTransfer(transfer));
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
            transferTerminalEventOutbox.recordIfAbsent(TransferFailedEvent.fromReservationRejected(
                    transfer, event.eventId(), event.reason(), java.time.Instant.now()));
        }
        eventInbox.recordProcessed(event);
    }

    private void applyReservationReleased(Transfer transfer, WorkflowEventRecord event) {
        var waitingForRelease = transfer.status() == TransferStatus.AWAITING_RESERVATION_RELEASE;
        var changed = transfer.accountReservationReleased(
                event.reservationRequestId(), event.reservationId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
            if (waitingForRelease) {
                var ledgerFailure = eventInbox.findLatestByTransferIdAndEventType(
                        transfer.id(), EventType.LEDGER_POSTING_FAILED);
                transferTerminalEventOutbox.recordIfAbsent(TransferFailedEvent.fromCompensatedLedgerFailure(
                        transfer,
                        event.eventId(),
                        ledgerFailure.map(WorkflowEventRecord::reason)
                                .orElse("Ledger Service rejected the posting request."),
                        java.time.Instant.now()));
            }
        }
        eventInbox.recordProcessed(event);
    }

    private void applyReservationExpired(Transfer transfer, WorkflowEventRecord event) {
        var changed = transfer.accountReservationExpired(
                event.reservationRequestId(), event.reservationId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
            transferTerminalEventOutbox.recordIfAbsent(TransferFailedEvent.fromReservationExpired(
                    transfer, event.eventId(), java.time.Instant.now()));
        }
        eventInbox.recordProcessed(event);
    }

    private void applyLedgerCompleted(Transfer transfer, WorkflowEventRecord event) {
        requireLedgerCompletionRecordMatches(transfer, event);
        var changed = transfer.ledgerPostingCompleted(event.postingRequestId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
            transferTerminalEventOutbox.recordIfAbsent(TransferCompletedEvent.from(
                    transfer, event.eventId(), event.postingId(), java.time.Instant.now()));
        }
        eventInbox.recordProcessed(event);
    }

    private void applyLedgerFailed(Transfer transfer, WorkflowEventRecord event, List<WorkflowAction> actions) {
        var changed = transfer.ledgerPostingFailed(event.postingRequestId(), event.correlationId());
        if (changed) {
            workflowRepository.save(transfer);
        }
        eventInbox.recordProcessed(event);
    }

    private void applyMfaAssurance(Transfer transfer, WorkflowEventRecord event, List<WorkflowAction> actions) {
        var changed = transfer.applyMfaAssurance(
                event.decisionId(),
                event.subjectId(),
                event.sourceAccountId(),
                event.destinationAccountId(),
                event.amount(),
                event.currency(),
                event.assuranceType(),
                event.challengeType(),
                event.policyVersion(),
                event.verifiedAt(),
                event.expiresAt(),
                java.time.Instant.now());
        if (changed) {
            workflowRepository.save(transfer);
            addAction(actions, transfer, RequestAccountReservation.forTransfer(transfer));
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

    private static void requireLedgerCompletionMatches(Transfer transfer, LedgerPostingCompleted event) {
        if (event.reservationRequestId() != null) {
            requireMatch(transfer.reservationRequestId(), event.reservationRequestId(), "reservationRequestId");
        }
        requireMatch(transfer.currency(), event.currency(), "currency");
        var debitLines = event.lines().stream().filter(line -> line.lineType().equals("DEBIT")).toList();
        var creditLines = event.lines().stream().filter(line -> line.lineType().equals("CREDIT")).toList();
        if (event.lines().size() != 2 || debitLines.size() != 1 || creditLines.size() != 1) {
            throw new TransferConflictException("ledger completion must contain exactly one debit and one credit line");
        }
        var debitLine = debitLines.getFirst();
        var creditLine = creditLines.getFirst();
        requireLedgerLine(transfer.sourceAccountId(), transfer.amount(), debitLine, "debit");
        requireLedgerLine(transfer.destinationAccountId(), transfer.amount(), creditLine, "credit");
    }

    private static void requireLedgerCompletionRecordMatches(Transfer transfer, WorkflowEventRecord event) {
        if (event.reservationRequestId() != null) {
            requireMatch(transfer.reservationRequestId(), event.reservationRequestId(), "reservationRequestId");
        }
        requireMatch(transfer.currency(), event.currency(), "currency");
        requireLedgerValue(transfer.sourceAccountId(), event.sourceAccountId(), "debit accountId");
        requireLedgerValue(transfer.destinationAccountId(), event.destinationAccountId(), "credit accountId");
        requireLedgerAmount(transfer.amount(), event.amount(), "ledger amount");
    }

    private static void requireReservationAcceptedMatches(Transfer transfer, WorkflowEventRecord event) {
        requireLedgerValue(transfer.sourceAccountId(), event.sourceAccountId(), "reservation source accountId");
        requireLedgerValue(
                transfer.destinationAccountId(), event.destinationAccountId(), "reservation destination accountId");
        requireLedgerAmount(transfer.amount(), event.amount(), "reservation amount");
        requireMatch(transfer.currency(), event.currency(), "reservation currency");
    }

    private static void requireLedgerLine(
            UUID expectedAccountId, java.math.BigDecimal expectedAmount, LedgerPostingCompleted.Line line, String label) {
        requireLedgerValue(expectedAccountId, line.accountId(), label + " accountId");
        requireLedgerAmount(expectedAmount, line.amount(), label + " amount");
    }

    private static void requireLedgerValue(Object expected, Object actual, String field) {
        if (!Objects.equals(expected, actual)) {
            throw new TransferConflictException(field + " does not match transfer workflow");
        }
    }

    private static void requireLedgerAmount(java.math.BigDecimal expected, java.math.BigDecimal actual, String field) {
        if (actual == null || expected.compareTo(actual) != 0) {
            throw new TransferConflictException(field + " does not match transfer workflow");
        }
    }

    private boolean isOutOfOrderLedgerEvent(Transfer transfer, WorkflowEventRecord event) {
        return transfer.status() == TransferStatus.PENDING
                && (event.eventType() == EventType.LEDGER_POSTING_COMPLETED
                        || event.eventType() == EventType.LEDGER_POSTING_FAILED);
    }

    private void requireEventIdentifiers(Transfer transfer, WorkflowEventRecord event) {
        if (event.eventType() == EventType.ACCOUNT_RESERVATION_CREATED
                || event.eventType() == EventType.ACCOUNT_RESERVATION_REJECTED
                || event.eventType() == EventType.ACCOUNT_RESERVATION_RELEASED
                || event.eventType() == EventType.ACCOUNT_RESERVATION_EXPIRED) {
            requireMatch(transfer.reservationRequestId(), event.reservationRequestId(), "reservationRequestId");
        }
        if (event.eventType() == EventType.LEDGER_POSTING_COMPLETED
                || event.eventType() == EventType.LEDGER_POSTING_FAILED) {
            requireMatch(transfer.postingRequestId(), event.postingRequestId(), "postingRequestId");
        }
        if (event.eventType() == EventType.MFA_ASSURANCE_GRANTED) {
            requireMfaAssurance(transfer, event);
            requireMatch(transfer.reservationRequestId(), event.reservationRequestId(), "reservationRequestId");
            requireMatch(transfer.riskDecision().decisionRequestId(), event.requestId(), "decisionRequestId");
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
                && Objects.equals(previous.postingId(), current.postingId())
                && Objects.equals(previous.reason(), current.reason())
                && Objects.equals(previous.decisionId(), current.decisionId())
                && Objects.equals(previous.subjectId(), current.subjectId())
                && Objects.equals(previous.challengeId(), current.challengeId())
                && Objects.equals(previous.assuranceType(), current.assuranceType())
                && Objects.equals(previous.challengeType(), current.challengeType())
                && Objects.equals(previous.sourceAccountId(), current.sourceAccountId())
                && Objects.equals(previous.destinationAccountId(), current.destinationAccountId())
                && Objects.equals(previous.amount(), current.amount())
                && Objects.equals(previous.currency(), current.currency())
                && Objects.equals(previous.verifiedAt(), current.verifiedAt())
                && Objects.equals(previous.expiresAt(), current.expiresAt())
                && Objects.equals(previous.policyVersion(), current.policyVersion());
    }

    private static void requireMfaAssurance(Transfer transfer, WorkflowEventRecord event) {
        var decision = transfer.riskDecision();
        if (decision == null || decision.outcome() != com.digitalbank.transactionservice.risk.TransferRiskOutcome.REQUIRE_STEP_UP) {
            throw new TransferConflictException("MFA assurance is not required for this transfer");
        }
        if (!Objects.equals(decision.decisionId(), event.decisionId())) {
            throw new TransferConflictException("decisionId does not match transfer risk decision");
        }
        if (event.subjectId() == null || !Objects.equals(transfer.customerId(), event.subjectId())) {
            throw new TransferConflictException("subjectId does not match transfer customer");
        }
        if (event.challengeId() == null || event.assuranceType() == null || event.challengeType() == null
                || event.sourceAccountId() == null || event.destinationAccountId() == null
                || event.amount() == null || event.currency() == null || event.verifiedAt() == null
                || event.expiresAt() == null || event.policyVersion() == null) {
            throw new TransferConflictException("MFA assurance event is incomplete");
        }
        if (!Objects.equals(decision.policyVersion(), event.policyVersion())
                || !Objects.equals(decision.requiredAssurance(), event.assuranceType())
                || !Objects.equals(decision.challengeType(), event.challengeType())
                || !Objects.equals(decision.expiresAt(), event.expiresAt())) {
            throw new TransferConflictException("MFA assurance policy window does not match risk decision");
        }
    }

    private void assertSameRequest(Transfer transfer, RequestTransferCommand command) {
        if (!transfer.id().equals(command.transferId())
                || !transfer.sourceAccountId().equals(command.sourceAccountId())
                || !transfer.destinationAccountId().equals(command.destinationAccountId())
                || transfer.amount().compareTo(command.amount()) != 0
                || !transfer.currency().equals(command.currency())
                || !transfer.customerId().equals(command.customerId())
                || !transfer.channel().equals(command.channel().toUpperCase())
                || transfer.destinationClass() != command.destinationClass()
                || !transfer.correlationId().equals(command.correlationId())
                || !transfer.transferRequestId().equals(command.transferRequestId())
                || !transfer.reservationRequestId().equals(command.reservationRequestId())
                || !transfer.postingRequestId().equals(command.postingRequestId())) {
            throw new TransferConflictException("Transfer workflow request conflicts with existing data");
        }
        if (transfer.riskDecision() != null
                && !transfer.riskDecision().decisionRequestId().equals(command.decisionRequestId())) {
            throw new TransferConflictException("Transfer risk decision conflicts with existing data");
        }
    }

    private List<WorkflowAction> record(Transfer transfer, WorkflowAction action) {
        var recorded = actionRepository.recordIfAbsent(action);
        recordReservationCommand(transfer, action);
        return recorded ? List.of(action) : List.of();
    }

    private void addAction(List<WorkflowAction> actions, Transfer transfer, WorkflowAction action) {
        if (actionRepository.recordIfAbsent(action)) {
            actions.add(action);
        }
        recordReservationCommand(transfer, action);
    }

    private void recordReservationCommand(Transfer transfer, WorkflowAction action) {
        if (action instanceof RequestAccountReservation) {
            reservationCommandEventOutbox.recordIfAbsent(AccountReservationRequestedEvent.from(transfer));
        } else if (action instanceof ReleaseAccountReservation) {
            reservationCommandEventOutbox.recordIfAbsent(AccountReservationReleaseRequestedEvent.from(transfer));
        } else if (action instanceof RequestLedgerPosting) {
            ledgerCommandEventOutbox.recordIfAbsent(LedgerPostingRequestedEvent.from(transfer));
        }
    }

    private static WorkflowResult result(Transfer transfer) {
        return new WorkflowResult(transfer, List.of());
    }

    private static WorkflowResult result(Transfer transfer, List<WorkflowAction> actions) {
        return new WorkflowResult(transfer, actions);
    }

    private static WorkflowResult result(Transfer transfer, List<WorkflowAction> actions, boolean created) {
        return new WorkflowResult(transfer, actions, created);
    }

    private static TransferRiskEvaluator legacyRiskEvaluator() {
        return new ConfiguredTransferRiskEvaluator(new TransferRiskProperties(
                "legacy-test-policy",
                java.time.Duration.ofMinutes(5),
                java.util.Map.of(),
                java.util.Set.of(),
                java.util.Set.of()));
    }

    private static final class NoOpReservationCommandEventOutbox implements ReservationCommandEventOutbox {
        @Override
        public boolean recordIfAbsent(com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent event) {
            return false;
        }

        @Override
        public List<com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent> claimReady(
                int limit, java.time.Instant now, UUID claimToken, java.time.Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent event,
                UUID claimToken, java.time.Instant publishedAt) {}

        @Override
        public void markFailed(com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent event,
                UUID claimToken, String error, java.time.Instant retryAt) {}
    }

    private static final class NoOpLedgerCommandEventOutbox implements LedgerCommandEventOutbox {
        @Override
        public boolean recordIfAbsent(com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent event) {
            return false;
        }

        @Override
        public List<com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent> claimReady(
                int limit, java.time.Instant now, UUID claimToken, java.time.Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent event,
                UUID claimToken, java.time.Instant publishedAt) {}

        @Override
        public void markFailed(com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent event,
                UUID claimToken, String error, java.time.Instant retryAt) {}
    }

    private static final class NoOpTransferTerminalEventOutbox implements TransferTerminalEventOutbox {
        @Override
        public boolean recordIfAbsent(TransferTerminalEvent event) {
            return false;
        }

        @Override
        public List<TransferTerminalEvent> claimReady(
                int limit, java.time.Instant now, UUID claimToken, java.time.Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(TransferTerminalEvent event, UUID claimToken, java.time.Instant publishedAt) {}

        @Override
        public void markFailed(TransferTerminalEvent event, UUID claimToken, String error, java.time.Instant retryAt) {}
    }
}
