package com.digitalbank.transactionservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.AccountReservationExpired;
import com.digitalbank.transactionservice.application.port.in.AccountReservationRejected;
import com.digitalbank.transactionservice.application.port.in.AccountReservationReleased;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingFailed;
import com.digitalbank.transactionservice.application.port.in.MfaAssuranceGranted;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEvent;
import com.digitalbank.transactionservice.application.port.out.ReservationCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.AccountReservationReleaseRequestedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEventOutbox;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEvent;
import com.digitalbank.transactionservice.application.port.out.TransferTerminalEventOutbox;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEvent;
import com.digitalbank.transactionservice.application.port.out.LedgerCommandEventOutbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.domain.Transfer;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import com.digitalbank.transactionservice.domain.TransferStatus;
import com.digitalbank.transactionservice.risk.TransferRiskDecision;
import com.digitalbank.transactionservice.risk.ConfiguredTransferRiskEvaluator;
import com.digitalbank.transactionservice.risk.TransferRiskEvaluator;
import com.digitalbank.transactionservice.risk.TransferRiskProperties;
import com.digitalbank.transactionservice.risk.TransferRiskOutcome;
import java.math.BigDecimal;
import java.time.Instant;
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
    private InMemoryTransferCreatedEventOutbox outbox;
    private InMemoryReservationCommandEventOutbox reservationOutbox;
    private InMemoryLedgerCommandEventOutbox ledgerOutbox;
    private InMemoryTransferTerminalEventOutbox terminalOutbox;
    private TransferProcessManager processManager;

    @BeforeEach
    void setUp() {
        workflows = new InMemoryWorkflowRepository();
        events = new InMemoryEventInbox();
        actions = new InMemoryActionRepository();
        outbox = new InMemoryTransferCreatedEventOutbox();
        reservationOutbox = new InMemoryReservationCommandEventOutbox();
        ledgerOutbox = new InMemoryLedgerCommandEventOutbox();
        terminalOutbox = new InMemoryTransferTerminalEventOutbox();
        processManager = new TransferProcessManager(workflows, events, actions, outbox,
                reservationOutbox, ledgerOutbox, terminalOutbox,
                defaultRiskEvaluator());
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
    void riskDecisionCanRequireStepUpWithoutRequestingReservation() {
        var manager = managerWithRiskDecision(TransferRiskOutcome.REQUIRE_STEP_UP);

        var result = manager.requestTransfer(command());

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().status().name()).isEqualTo("AWAITING_STEP_UP");
        assertThat(result.transfer().riskDecision().outcome()).isEqualTo(TransferRiskOutcome.REQUIRE_STEP_UP);
        assertThat(result.actions()).isEmpty();
        assertThat(actions.actions()).isEmpty();
    }

    @Test
    void grantedMfaAssuranceResumesStepUpTransferAndRequestsReservation() {
        var now = Instant.now();
        var decision = new TransferRiskDecision(
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "transfer-request-001", TRANSFER_ID, TransferRiskOutcome.REQUIRE_STEP_UP,
                List.of("HIGH_VALUE"), "MFA", "TOTP", "test-policy", now, now.plusSeconds(300), CORRELATION_ID);
        TransferRiskEvaluator evaluator = (intent, ignored) -> decision;
        var manager = new TransferProcessManager(workflows, events, actions, outbox,
                new InMemoryReservationCommandEventOutbox(), ledgerOutbox, evaluator);

        manager.requestTransfer(command());
        var result = manager.handle(new MfaAssuranceGranted(
                TRANSFER_ID, "event-mfa-001", CORRELATION_ID, "transfer-request-001", RESERVATION_REQUEST_ID,
                decision.decisionId(), "system", "challenge-001", "MFA", SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID, new BigDecimal("12.50"), "AED", "TOTP", now.minusSeconds(1),
                decision.expiresAt(), decision.policyVersion()));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(result.actions()).singleElement()
                .extracting(WorkflowAction::actionId)
                .isEqualTo("account-reservation:" + RESERVATION_REQUEST_ID);
    }

    @Test
    void mfaAssuranceWithDifferentChallengeTypeIsRejected() {
        var now = Instant.now();
        var decision = new TransferRiskDecision(
                UUID.fromString("55555555-5555-5555-5555-555555555555"),
                "transfer-request-001", TRANSFER_ID, TransferRiskOutcome.REQUIRE_STEP_UP,
                List.of("HIGH_VALUE"), "MFA", "TOTP", "test-policy", now, now.plusSeconds(300), CORRELATION_ID);
        var manager = new TransferProcessManager(workflows, events, actions, outbox,
                new InMemoryReservationCommandEventOutbox(), ledgerOutbox, (intent, ignored) -> decision);
        manager.requestTransfer(command());

        assertThatThrownBy(() -> manager.handle(new MfaAssuranceGranted(
                TRANSFER_ID, "event-mfa-002", CORRELATION_ID, "transfer-request-001", RESERVATION_REQUEST_ID,
                decision.decisionId(), "system", "challenge-002", "MFA", SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID, new BigDecimal("12.50"), "AED", "SMS", now.minusSeconds(1),
                decision.expiresAt(), decision.policyVersion())))
                .isInstanceOf(TransferConflictException.class);
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status()).isEqualTo(TransferStatus.AWAITING_STEP_UP);
    }

    @Test
    void declinedRiskDecisionFailsWithoutRequestingReservation() {
        var manager = managerWithRiskDecision(TransferRiskOutcome.DECLINE);

        var result = manager.requestTransfer(command());

        assertThat(result.created()).isTrue();
        assertThat(result.transfer().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.transfer().riskDecision().outcome()).isEqualTo(TransferRiskOutcome.DECLINE);
        assertThat(result.actions()).isEmpty();
        assertThat(actions.actions()).isEmpty();
    }

    @Test
    void requestTransferRecordsTransferCreatedEventInOutbox() {
        processManager.requestTransfer(command());

        assertThat(outbox.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("TransferCreated.v1");
            assertThat(event.schemaVersion()).isEqualTo("1.0.0");
            assertThat(event.producer()).isEqualTo("transaction-service");
            assertThat(event.aggregateId()).isEqualTo(TRANSFER_ID);
            assertThat(event.transactionId()).isEqualTo(TRANSFER_ID);
            assertThat(event.causationId()).isEqualTo("transfer-request-001");
            assertThat(event.status()).isEqualTo(TransferStatus.PENDING);
        });
    }

    @Test
    void repeatedTransferRequestIsIdempotent() {
        processManager.requestTransfer(command());

        var retry = processManager.requestTransfer(command());

        assertThat(retry.transfer().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(retry.actions()).isEmpty();
        assertThat(actions.actions()).hasSize(1);
        assertThat(outbox.events()).hasSize(1);
    }

    @Test
    void findsExistingTransferWorkflowWithoutCreatingActions() {
        processManager.requestTransfer(command());

        var found = processManager.findTransferWorkflow(TRANSFER_ID);

        assertThat(found).hasValueSatisfying(result -> {
            assertThat(result.transfer().id()).isEqualTo(TRANSFER_ID);
            assertThat(result.transfer().status()).isEqualTo(TransferStatus.PENDING);
            assertThat(result.actions()).isEmpty();
        });
        assertThat(actions.actions()).hasSize(1);
    }

    @Test
    void returnsEmptyWhenTransferWorkflowIsNotFound() {
        var found = processManager.findTransferWorkflow(TRANSFER_ID);

        assertThat(found).isEmpty();
        assertThat(actions.actions()).isEmpty();
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
        assertThat(ledgerOutbox.events()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("LedgerPostingRequested.v1");
            assertThat(event.aggregateId()).isEqualTo(POSTING_REQUEST_ID);
            assertThat(event.postingRequestId()).isEqualTo(POSTING_REQUEST_ID);
            assertThat(event.reservationRequestId()).isEqualTo(RESERVATION_REQUEST_ID);
            assertThat(event.reservationId()).isEqualTo("reservation-001");
        });
    }

    @Test
    void reservationAcceptedWithMismatchedDetailsIsRejectedBeforeLedgerPosting() {
        processManager.requestTransfer(command());

        assertThatThrownBy(() -> processManager.handle(new AccountReservationAccepted(
                TRANSFER_ID,
                "event-reservation-mismatched-details",
                CORRELATION_ID,
                RESERVATION_REQUEST_ID,
                "reservation-001",
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal("12.51"),
                "AED",
                Instant.now().plusSeconds(300))))
                .isInstanceOf(TransferConflictException.class);
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(events.eventIds()).doesNotContain("event-reservation-mismatched-details");
        assertThat(ledgerOutbox.events()).isEmpty();
    }

    @Test
    void reservationAcceptedReplayWithChangedPayloadIsRejected() {
        processManager.requestTransfer(command());
        var firstExpiry = Instant.now().plusSeconds(300);
        var first = new AccountReservationAccepted(
                TRANSFER_ID,
                "event-reservation-replay-conflict",
                CORRELATION_ID,
                RESERVATION_REQUEST_ID,
                "reservation-001",
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal("12.50"),
                "AED",
                firstExpiry);
        processManager.handle(first);

        assertThatThrownBy(() -> processManager.handle(new AccountReservationAccepted(
                TRANSFER_ID,
                first.eventId(),
                CORRELATION_ID,
                RESERVATION_REQUEST_ID,
                "reservation-001",
                SOURCE_ACCOUNT_ID,
                DESTINATION_ACCOUNT_ID,
                new BigDecimal("12.50"),
                "AED",
                firstExpiry.plusSeconds(1))))
                .isInstanceOf(TransferConflictException.class)
                .hasMessageContaining("eventId");
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status())
                .isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
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

        var result = processManager.handle(ledgerCompletion("event-ledger-001"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void ledgerCompletionRecordsOneTerminalCompletedEvent() {
        reserve();

        processManager.handle(ledgerCompletion("event-ledger-terminal-completed"));
        processManager.handle(ledgerCompletion("event-ledger-terminal-retry"));

        assertThat(terminalOutbox.events()).singleElement().satisfies(event -> {
            assertThat(event).isInstanceOf(com.digitalbank.transactionservice.application.port.out.TransferCompletedEvent.class);
            assertThat(event.eventType()).isEqualTo("TransferCompleted.v1");
            assertThat(event.status()).isEqualTo("COMPLETED");
            assertThat(event.postingId()).isEqualTo("posting-001");
            assertThat(event.causationId()).isEqualTo("event-ledger-terminal-completed");
        });
    }

    @Test
    void ledgerCompletionWithMismatchedDestinationIsRejectedWithoutProcessingEvent() {
        reserve();

        assertThatThrownBy(() -> processManager.handle(new LedgerPostingCompleted(
                TRANSFER_ID,
                "event-ledger-wrong-destination",
                CORRELATION_ID,
                "ledger-event-request-wrong-destination",
                "posting-001",
                POSTING_REQUEST_ID,
                RESERVATION_REQUEST_ID,
                "AED",
                List.of(
                        new LedgerPostingCompleted.Line(SOURCE_ACCOUNT_ID, "DEBIT", new BigDecimal("12.50")),
                        new LedgerPostingCompleted.Line(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                "CREDIT",
                                new BigDecimal("12.50"))))))
                .isInstanceOf(TransferConflictException.class);
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status())
                .isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(events.eventIds()).doesNotContain("event-ledger-wrong-destination");
    }

    @Test
    void ledgerCompletionWithMismatchedAmountIsRejectedWithoutProcessingEvent() {
        reserve();

        assertThatThrownBy(() -> processManager.handle(new LedgerPostingCompleted(
                TRANSFER_ID,
                "event-ledger-wrong-amount",
                CORRELATION_ID,
                "ledger-event-request-wrong-amount",
                "posting-001",
                POSTING_REQUEST_ID,
                RESERVATION_REQUEST_ID,
                "AED",
                List.of(
                        new LedgerPostingCompleted.Line(SOURCE_ACCOUNT_ID, "DEBIT", new BigDecimal("12.50")),
                        new LedgerPostingCompleted.Line(DESTINATION_ACCOUNT_ID, "CREDIT", new BigDecimal("12.51"))))))
                .isInstanceOf(TransferConflictException.class);
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status())
                .isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(events.eventIds()).doesNotContain("event-ledger-wrong-amount");
    }

    @Test
    void ledgerCompletionWithMismatchedCurrencyIsRejectedWithoutProcessingEvent() {
        reserve();

        assertThatThrownBy(() -> processManager.handle(new LedgerPostingCompleted(
                TRANSFER_ID,
                "event-ledger-wrong-currency",
                CORRELATION_ID,
                "ledger-event-request-wrong-currency",
                "posting-001",
                POSTING_REQUEST_ID,
                RESERVATION_REQUEST_ID,
                "USD",
                List.of(
                        new LedgerPostingCompleted.Line(SOURCE_ACCOUNT_ID, "DEBIT", new BigDecimal("12.50")),
                        new LedgerPostingCompleted.Line(DESTINATION_ACCOUNT_ID, "CREDIT", new BigDecimal("12.50"))))))
                .isInstanceOf(TransferConflictException.class);
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status())
                .isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(events.eventIds()).doesNotContain("event-ledger-wrong-currency");
    }

    @Test
    void ledgerCompletionWithExtraLineIsRejectedWithoutProcessingEvent() {
        reserve();

        assertThatThrownBy(() -> processManager.handle(new LedgerPostingCompleted(
                TRANSFER_ID,
                "event-ledger-extra-line",
                CORRELATION_ID,
                "ledger-event-request-extra-line",
                "posting-001",
                POSTING_REQUEST_ID,
                RESERVATION_REQUEST_ID,
                "AED",
                List.of(
                        new LedgerPostingCompleted.Line(SOURCE_ACCOUNT_ID, "DEBIT", new BigDecimal("12.50")),
                        new LedgerPostingCompleted.Line(DESTINATION_ACCOUNT_ID, "CREDIT", new BigDecimal("12.49")),
                        new LedgerPostingCompleted.Line(
                                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                                "CREDIT",
                                new BigDecimal("0.01"))))))
                .isInstanceOf(TransferConflictException.class);
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().status())
                .isEqualTo(TransferStatus.AWAITING_LEDGER_POSTING);
        assertThat(events.eventIds()).doesNotContain("event-ledger-extra-line");
    }

    @Test
    void ledgerFailureRequestsAccountOwnedReservationRelease() {
        reserve();

        var result = processManager.handle(new LedgerPostingFailed(
                TRANSFER_ID,
                "event-ledger-002",
                CORRELATION_ID,
                "ledger-event-request-002",
                POSTING_REQUEST_ID,
                "unbalanced posting"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.AWAITING_RESERVATION_RELEASE);
        assertThat(result.actions()).singleElement()
                .extracting(WorkflowAction::actionId)
                .isEqualTo("release-reservation:reservation-001");
        assertThat(actions.actions()).extracting(WorkflowAction::actionId)
                .contains("release-reservation:reservation-001");
        assertThat(reservationOutbox.events())
                .filteredOn(event -> event instanceof AccountReservationReleaseRequestedEvent)
                .singleElement()
                .isInstanceOfSatisfying(AccountReservationReleaseRequestedEvent.class, event -> {
                    assertThat(event.eventType()).isEqualTo(AccountReservationReleaseRequestedEvent.EVENT_TYPE);
                    assertThat(event.reservationId()).isEqualTo("reservation-001");
                    assertThat(event.reason()).isEqualTo("LEDGER_POSTING_FAILED");
                });
    }

    @Test
    void releasedReservationCompletesLedgerFailureTransition() {
        reserve();
        processManager.handle(new LedgerPostingFailed(
                TRANSFER_ID,
                "event-ledger-release-001",
                CORRELATION_ID,
                "ledger-event-request-release-001",
                POSTING_REQUEST_ID,
                "unbalanced posting"));

        var result = processManager.handle(new AccountReservationReleased(
                TRANSFER_ID, "event-reservation-released-001", CORRELATION_ID,
                RESERVATION_REQUEST_ID, "reservation-001"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void releasedReservationRecordsCompensatedTerminalFailureEvent() {
        reserve();
        processManager.handle(new LedgerPostingFailed(
                TRANSFER_ID,
                "event-ledger-terminal-failed",
                CORRELATION_ID,
                "ledger-event-request-terminal-failed",
                POSTING_REQUEST_ID,
                "unbalanced posting"));

        processManager.handle(new AccountReservationReleased(
                TRANSFER_ID, "event-reservation-terminal-released", CORRELATION_ID,
                RESERVATION_REQUEST_ID, "reservation-001"));

        assertThat(terminalOutbox.events()).singleElement().satisfies(event -> {
            assertThat(event).isInstanceOf(com.digitalbank.transactionservice.application.port.out.TransferFailedEvent.class);
            assertThat(event.eventType()).isEqualTo("TransferFailed.v1");
            assertThat(event.status()).isEqualTo("FAILED");
            assertThat(event.failureCode()).isEqualTo("LEDGER_POSTING_FAILED");
            assertThat(event.compensationStatus()).isEqualTo("COMPLETED");
            assertThat(event.manualReviewRequired()).isFalse();
        });
    }

    @Test
    void expiredReservationIsTerminalWithoutReleaseAction() {
        reserve();

        var result = processManager.handle(new AccountReservationExpired(
                TRANSFER_ID, "event-reservation-expired-001", CORRELATION_ID,
                RESERVATION_REQUEST_ID, "reservation-001"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.FAILED);
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void duplicateEventIdDoesNotRepeatTransitionOrAction() {
        reserve();
        var event = ledgerCompletion("event-ledger-003");

        processManager.handle(event);
        var retry = processManager.handle(event);

        assertThat(retry.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(retry.actions()).isEmpty();
        assertThat(workflows.findById(TRANSFER_ID).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    void conflictingLedgerPostingIdWithSameEventIdIsRejected() {
        reserve();
        var event = ledgerCompletion("event-ledger-posting-conflict");
        processManager.handle(event);

        var conflicting = new LedgerPostingCompleted(
                TRANSFER_ID,
                event.eventId(),
                CORRELATION_ID,
                event.requestId(),
                "posting-002",
                POSTING_REQUEST_ID,
                RESERVATION_REQUEST_ID,
                "AED",
                event.lines());

        assertThatThrownBy(() -> processManager.handle(conflicting))
                .isInstanceOf(TransferConflictException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void usesAtomicWorkflowCreationBeforeReadingAnExistingRequest() {
        var repository = new AtomicCreationOnlyWorkflowRepository();
        var manager = new TransferProcessManager(repository, events, actions, outbox);

        var result = manager.requestTransfer(command());

        assertThat(result.transfer().id()).isEqualTo(TRANSFER_ID);
        assertThat(result.actions()).hasSize(1);
        assertThat(repository.saveIfAbsentCalled).isTrue();
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

        processManager.handle(ledgerCompletion("event-ledger-004"));

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

    private TransferProcessManager managerWithRiskDecision(TransferRiskOutcome outcome) {
        TransferRiskEvaluator evaluator = (intent, now) -> new TransferRiskDecision(
                UUID.nameUUIDFromBytes(("decision:" + outcome).getBytes()),
                intent.decisionRequestId(),
                intent.transferId(),
                outcome,
                List.of("TEST_POLICY"),
                outcome == TransferRiskOutcome.REQUIRE_STEP_UP ? "MFA" : null,
                outcome == TransferRiskOutcome.REQUIRE_STEP_UP ? "TOTP" : null,
                "test-policy",
                now,
                now.plusSeconds(300),
                intent.correlationId());
        return new TransferProcessManager(workflows, events, actions, outbox,
                new InMemoryReservationCommandEventOutbox(), ledgerOutbox, evaluator);
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

    private static TransferRiskEvaluator defaultRiskEvaluator() {
        return new ConfiguredTransferRiskEvaluator(new TransferRiskProperties(
                "test-policy",
                java.time.Duration.ofMinutes(5),
                Map.of(),
                Set.of(),
                Set.of()));
    }

    private static LedgerPostingCompleted ledgerCompletion(String eventId) {
        return new LedgerPostingCompleted(
                TRANSFER_ID,
                eventId,
                CORRELATION_ID,
                "ledger-event-request-" + eventId,
                "posting-001",
                POSTING_REQUEST_ID,
                RESERVATION_REQUEST_ID,
                "AED",
                List.of(
                        new LedgerPostingCompleted.Line(SOURCE_ACCOUNT_ID, "DEBIT", new BigDecimal("12.50")),
                        new LedgerPostingCompleted.Line(DESTINATION_ACCOUNT_ID, "CREDIT", new BigDecimal("12.50"))));
    }

    private static final class InMemoryWorkflowRepository implements TransferWorkflowRepository {
        private final Map<UUID, Transfer> values = new HashMap<>();

        @Override
        public Optional<Transfer> findById(UUID transferId) {
            return Optional.ofNullable(values.get(transferId));
        }

        @Override
        public synchronized Transfer saveIfAbsent(Transfer transfer) {
            return values.computeIfAbsent(transfer.id(), ignored -> transfer);
        }

        @Override
        public Transfer save(Transfer transfer) {
            values.put(transfer.id(), transfer);
            return transfer;
        }
    }

    private static final class InMemoryTransferCreatedEventOutbox implements TransferCreatedEventOutbox {
        private final List<TransferCreatedEvent> values = new ArrayList<>();

        @Override
        public boolean recordIfAbsent(TransferCreatedEvent event) {
            if (values.stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) {
                return false;
            }
            values.add(event);
            return true;
        }

        List<TransferCreatedEvent> events() {
            return values;
        }
    }

    private static final class AtomicCreationOnlyWorkflowRepository implements TransferWorkflowRepository {

        private boolean saveIfAbsentCalled;

        @Override
        public Optional<Transfer> findById(UUID transferId) {
            throw new AssertionError("requestTransfer must use atomic creation before lookup");
        }

        @Override
        public Transfer saveIfAbsent(Transfer transfer) {
            saveIfAbsentCalled = true;
            return transfer;
        }

        @Override
        public Transfer save(Transfer transfer) {
            throw new AssertionError("requestTransfer must not save the initial workflow twice");
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

    private static final class InMemoryReservationCommandEventOutbox implements ReservationCommandEventOutbox {
        private final List<ReservationCommandEvent> values = new ArrayList<>();

        @Override
        public boolean recordIfAbsent(ReservationCommandEvent event) {
            if (values.stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) {
                return false;
            }
            values.add(event);
            return true;
        }

        @Override
        public List<ReservationCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(ReservationCommandEvent event, UUID claimToken, Instant publishedAt) {}

        @Override
        public void markFailed(ReservationCommandEvent event, UUID claimToken, String error, Instant retryAt) {}

        List<ReservationCommandEvent> events() {
            return values;
        }
    }

    private static final class InMemoryLedgerCommandEventOutbox implements LedgerCommandEventOutbox {
        private final List<LedgerCommandEvent> values = new ArrayList<>();

        @Override
        public boolean recordIfAbsent(LedgerCommandEvent event) {
            if (values.stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) {
                return false;
            }
            values.add(event);
            return true;
        }

        @Override
        public List<LedgerCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(LedgerCommandEvent event, UUID claimToken, Instant publishedAt) {}

        @Override
        public void markFailed(LedgerCommandEvent event, UUID claimToken, String error, Instant retryAt) {}

        List<LedgerCommandEvent> events() {
            return values;
        }
    }

    private static final class InMemoryTransferTerminalEventOutbox implements TransferTerminalEventOutbox {
        private final List<TransferTerminalEvent> values = new ArrayList<>();

        @Override
        public boolean recordIfAbsent(TransferTerminalEvent event) {
            if (values.stream().anyMatch(existing -> existing.eventId().equals(event.eventId()))) {
                return false;
            }
            values.add(event);
            return true;
        }

        @Override
        public List<TransferTerminalEvent> claimReady(
                int limit, Instant now, UUID claimToken, Instant leaseUntil) {
            return List.of();
        }

        @Override
        public void markPublished(TransferTerminalEvent event, UUID claimToken, Instant publishedAt) {}

        @Override
        public void markFailed(TransferTerminalEvent event, UUID claimToken, String error, Instant retryAt) {}

        List<TransferTerminalEvent> events() {
            return values;
        }
    }
}
