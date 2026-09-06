package com.digitalbank.transactionservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.AccountReservationAccepted;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEvent;
import com.digitalbank.transactionservice.application.port.out.TransferCreatedEventOutbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.digitalbank.transactionservice.application.service.WorkflowResult;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import com.digitalbank.transactionservice.domain.TransferStatus;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
class TransferWorkflowPersistenceIT {

    private UUID transferId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private String correlationId;
    private String transferRequestId;
    private String reservationRequestId;
    private String postingRequestId;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TransferProcessManager processManager;

    @Autowired
    private TransferWorkflowRepository workflowRepository;

    @Autowired
    private WorkflowEventInbox eventInbox;

    @Autowired
    private TransferCreatedEventOutbox transferCreatedEventOutbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        transferId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        destinationAccountId = UUID.randomUUID();
        correlationId = "persistence-correlation-" + UUID.randomUUID();
        transferRequestId = "transfer-request-" + UUID.randomUUID();
        reservationRequestId = "reservation-request-" + UUID.randomUUID();
        postingRequestId = "posting-request-" + UUID.randomUUID();
    }

    @Test
    void persistsWorkflowActionAndReloadableState() {
        processManager.requestTransfer(command());
        processManager.requestTransfer(command());

        var stored = workflowRepository.findById(transferId);

        assertThat(stored).hasValueSatisfying(transfer -> {
            assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
            assertThat(transfer.correlationId()).isEqualTo(correlationId);
            assertThat(transfer.version()).isZero();
        });
        assertThat(count("transfer_workflow_actions", transferId)).isEqualTo(1);
        assertThat(countByColumn("transfer_created_event_outbox", "aggregate_id", transferId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select event_type from transfer_created_event_outbox where aggregate_id = ?",
                String.class,
                transferId)).isEqualTo("TransferCreated.v1");
    }

    @Test
    void concurrentDuplicateTransferRequestsPersistOneWorkflowAndAction() throws Exception {
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<WorkflowResult>> requests =
                    List.of(
                            executor.submit(() -> requestAfter(start)),
                            executor.submit(() -> requestAfter(start)));
            start.countDown();

            var results = List.of(requests.get(0).get(), requests.get(1).get());

            assertThat(results).allSatisfy(result -> assertThat(result.transfer().id()).isEqualTo(transferId));
            assertThat(results.stream().mapToInt(result -> result.actions().size()).sum()).isEqualTo(1);
            assertThat(countByPrimaryKey("transfer_workflows", transferId)).isEqualTo(1);
            assertThat(count("transfer_workflow_actions", transferId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentOutboxClaimsDeliverAnEventToOnlyOneWorker() throws Exception {
        processManager.requestTransfer(command());
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<List<TransferCreatedEvent>>> claims = List.of(
                    executor.submit(() -> claimAfter(start)),
                    executor.submit(() -> claimAfter(start)));
            start.countDown();

            var claimed = List.of(claims.get(0).get(), claims.get(1).get());

            assertThat(claimed.stream().mapToInt(List::size).sum()).isEqualTo(1);
            assertThat(claimed.stream().filter(events -> !events.isEmpty())).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void persistsDeferredEventAndReplaysItAfterReservation() {
        processManager.requestTransfer(command());
        var ledgerEvent = new LedgerPostingCompleted(
                transferId,
                "persisted-ledger-event",
                correlationId,
                "ledger-event-request",
                "posting-001",
                postingRequestId,
                reservationRequestId,
                "AED",
                List.of(
                        new LedgerPostingCompleted.Line(sourceAccountId, "DEBIT", new BigDecimal("17.25")),
                        new LedgerPostingCompleted.Line(destinationAccountId, "CREDIT", new BigDecimal("17.25"))));
        processManager.handle(ledgerEvent);

        assertThat(eventInbox.findByEventId(ledgerEvent.eventId()))
                .hasValueSatisfying(event -> assertThat(event.postingId()).isEqualTo("posting-001"));
        assertThat(eventInbox.findDeferredByTransferId(transferId)).hasSize(1);

        var result = processManager.handle(new AccountReservationCreated(
                transferId,
                "persisted-reservation-event",
                correlationId,
                reservationRequestId,
                "reservation-001"));

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(eventInbox.findDeferredByTransferId(transferId)).isEmpty();
        assertThat(count("transfer_workflow_actions", transferId)).isEqualTo(1);
    }

    @Test
    void persistsReservationAcceptanceDetailsForReplayConflictDetection() {
        processManager.requestTransfer(command());
        var expiry = Instant.now().plusSeconds(300).truncatedTo(ChronoUnit.MICROS);
        var accepted = new AccountReservationAccepted(
                transferId,
                "persisted-reservation-accepted",
                correlationId,
                reservationRequestId,
                "reservation-001",
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("17.25"),
                "AED",
                expiry);

        processManager.handle(accepted);

        assertThat(eventInbox.findByEventId(accepted.eventId())).hasValueSatisfying(event -> {
            assertThat(event.sourceAccountId()).isEqualTo(sourceAccountId);
            assertThat(event.destinationAccountId()).isEqualTo(destinationAccountId);
            assertThat(event.amount()).isEqualByComparingTo("17.25");
            assertThat(event.currency()).isEqualTo("AED");
            assertThat(event.expiresAt()).isEqualTo(expiry);
        });
        assertThatThrownBy(() -> processManager.handle(new AccountReservationAccepted(
                transferId,
                accepted.eventId(),
                correlationId,
                reservationRequestId,
                "reservation-001",
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("17.25"),
                "AED",
                expiry.plusSeconds(1))))
                .isInstanceOf(TransferConflictException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void rejectsStaleWorkflowUpdateWithOptimisticLocking() {
        processManager.requestTransfer(command());
        var current = workflowRepository.findById(transferId).orElseThrow();
        var stale = workflowRepository.findById(transferId).orElseThrow();

        current.accountReservationRejected();
        workflowRepository.save(current);

        stale.accountReservationCreated(reservationRequestId, "reservation-002", correlationId);

        assertThatThrownBy(() -> workflowRepository.save(stale))
                .isInstanceOf(JpaOptimisticLockingFailureException.class)
                .hasRootCauseInstanceOf(OptimisticLockException.class);
    }

    private RequestTransferCommand command() {
        return new RequestTransferCommand(
                transferId,
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("17.25"),
                "AED",
                correlationId,
                transferRequestId,
                reservationRequestId,
                postingRequestId);
    }

    private WorkflowResult requestAfter(CountDownLatch start)
            throws InterruptedException {
        start.await();
        return processManager.requestTransfer(command());
    }

    private List<TransferCreatedEvent> claimAfter(CountDownLatch start)
            throws InterruptedException {
        start.await();
        return transferCreatedEventOutbox.claimReady(
                1, Instant.now(), UUID.randomUUID(), Instant.now().plusSeconds(60));
    }

    private int count(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where transfer_id = ?", Integer.class, id);
    }

    private int countByPrimaryKey(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where id = ?", Integer.class, id);
    }

    private int countByColumn(String table, String column, UUID id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?", Integer.class, id);
    }
}
