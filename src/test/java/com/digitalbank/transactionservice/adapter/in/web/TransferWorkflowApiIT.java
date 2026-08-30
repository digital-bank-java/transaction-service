package com.digitalbank.transactionservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class TransferWorkflowApiIT {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private TransferWorkflowRepository workflowRepository;

    @Test
    void postsTransferWorkflowThroughHttpAndPersistsIt() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();

        var firstResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 25.25,
                                  "currency": "AED",
                                  "correlationId": "api-correlation-%s",
                                  "transferRequestId": "api-transfer-request-%s",
                                  "reservationRequestId": "api-reservation-request-%s",
                                  "postingRequestId": "api-posting-request-%s"
                                }
                                """
                                        .formatted(
                                                transferId,
                                                sourceAccountId,
                                                destinationAccountId,
                                                transferId,
                                                transferId,
                                                transferId,
                                                transferId)))
                .andReturn()
                .getResponse();

        assertThat(firstResponse.getStatus()).isEqualTo(201);
        assertThat(firstResponse.getHeader("Content-Type")).startsWith(APPLICATION_JSON.toString());
        var created = objectMapper.readTree(firstResponse.getContentAsString());
        assertThat(created.path("transferId").asText()).isEqualTo(transferId.toString());
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(created.path("actions")).hasSize(1);
        assertThat(created.path("actions").get(0).path("type").asText()).isEqualTo("REQUEST_ACCOUNT_RESERVATION");

        assertThat(workflowRepository.findById(transferId))
                .hasValueSatisfying(transfer -> assertThat(transfer.correlationId()).isEqualTo("api-correlation-" + transferId));

        var replayResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 25.25,
                                  "currency": "AED",
                                  "correlationId": "api-correlation-%s",
                                  "transferRequestId": "api-transfer-request-%s",
                                  "reservationRequestId": "api-reservation-request-%s",
                                  "postingRequestId": "api-posting-request-%s"
                                }
                                """
                                        .formatted(
                                                transferId,
                                                sourceAccountId,
                                                destinationAccountId,
                                                transferId,
                                                transferId,
                                                transferId,
                                                transferId)))
                .andReturn()
                .getResponse();

        assertThat(replayResponse.getStatus()).isEqualTo(200);
        assertThat(replayResponse.getHeader("Idempotent-Replay")).isEqualTo("true");
        var replay = objectMapper.readTree(replayResponse.getContentAsString());
        assertThat(replay.path("transferId").asText()).isEqualTo(transferId.toString());
        assertThat(replay.path("actions")).isEmpty();
    }
}
