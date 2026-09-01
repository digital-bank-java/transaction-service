package com.digitalbank.transactionservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "spring.cloud.config.enabled=false")
class TransferWorkflowPostgresApiIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentIdenticalRequestsCreateOneWorkflowAndOneInitialAction() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        var request = """
                {
                  "transferId": "%s",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": 25.25,
                  "currency": "AED",
                  "correlationId": "postgres-concurrency-%s",
                  "transferRequestId": "postgres-transfer-request-%s",
                  "reservationRequestId": "postgres-reservation-request-%s",
                  "postingRequestId": "postgres-posting-request-%s"
                }
                """.formatted(
                transferId,
                sourceAccountId,
                destinationAccountId,
                transferId,
                transferId,
                transferId,
                transferId);

        var callers = 8;
        var barrier = new CyclicBarrier(callers);
        var executor = Executors.newFixedThreadPool(callers);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for (var index = 0; index < callers; index++) {
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return mockMvc.perform(post("/internal/v1/transfer-workflows")
                                    .contentType(APPLICATION_JSON)
                                    .content(request))
                            .andReturn();
                }));
            }

            var responses = new ArrayList<MvcResult>();
            for (var future : futures) {
                responses.add(future.get());
            }

            assertThat(responses)
                    .extracting(MvcResult::getResponse)
                    .allSatisfy(response -> assertThat(response.getStatus()).isIn(200, 201));
            assertThat(responses.stream().filter(result -> result.getResponse().getStatus() == 201)).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from transfer_workflows where id = ?", Integer.class, transferId))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from transfer_workflow_actions where transfer_id = ?", Integer.class, transferId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
