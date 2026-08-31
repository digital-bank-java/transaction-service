package com.digitalbank.transactionservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.transactionservice.TestSecurityConfig;
import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.domain.TransferStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@Import(TestSecurityConfig.class)
class TransferProcessManagerSpringIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private TransferProcessManager processManager;

    @Autowired
    private TransferWorkflowRepository workflowRepository;

    @Test
    void springWiringPersistsInitialTransferAndAction() {
        var transferId = UUID.randomUUID();
        var command = new RequestTransferCommand(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("5.00"),
                "AED",
                "spring-correlation-" + transferId,
                "spring-transfer-request-" + transferId,
                "spring-reservation-request-" + transferId,
                "spring-posting-request-" + transferId);

        var result = processManager.requestTransfer(command);

        assertThat(result.transfer().status()).isEqualTo(TransferStatus.PENDING);
        assertThat(result.actions()).hasSize(1);
        assertThat(workflowRepository.findById(transferId)).hasValueSatisfying(transfer ->
                assertThat(transfer.correlationId()).isEqualTo(command.correlationId()));
    }
}
