package com.digitalbank.transactionservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.digitalbank.transactionservice.application.port.in.AccountReservationCreated;
import com.digitalbank.transactionservice.application.port.in.LedgerPostingCompleted;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.digitalbank.transactionservice.domain.TransferStatus;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
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

        var stored = workflowRepository.findById(transferId);

        assertThat(stored).hasValueSatisfying(transfer -> {
            assertThat(transfer.status()).isEqualTo(TransferStatus.PENDING);
            assertThat(transfer.correlationId()).isEqualTo(correlationId);
            assertThat(transfer.version()).isZero();
        });
        assertThat(count("transfer_workflow_actions", transferId)).isEqualTo(1);
    }

    @Test
    void persistsDeferredEventAndReplaysItAfterReservation() {
        processManager.requestTransfer(command());
        processManager.handle(new LedgerPostingCompleted(
                transferId, "persisted-ledger-event", correlationId, "ledger-event-request", postingRequestId));

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

    private int count(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from " + table + " where transfer_id = ?", Integer.class, id);
    }
}
