package com.digitalbank.transactionservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalbank.transactionservice.TestSecurityConfig;
import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.domain.Transfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
@TestPropertySource(properties = "transaction.transfer.authorization.allowed-subjects=transfer-orchestrator")
class TransferWorkflowApiIT {

    private static final String TEST_DATABASE =
            "jdbc:h2:mem:transfer-workflow-api-it-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private TransferWorkflowRepository workflowRepository;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TEST_DATABASE);
    }

    @Test
    void postsTransferWorkflowThroughHttpAndPersistsIt() throws Exception {
        var scenarioId = UUID.randomUUID();
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();

        var firstResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                    "/internal/v1/transfer-workflows")
                        .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.AUTHORIZED_AUTHORIZATION)
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
                                                scenarioId,
                                                scenarioId,
                                                scenarioId,
                                                scenarioId)))
                .andReturn()
                .getResponse();

        assertThat(firstResponse.getStatus()).isEqualTo(201);
        assertThat(firstResponse.getHeader("Content-Type")).startsWith(APPLICATION_JSON.toString());
        assertThat(firstResponse.getHeader("Location")).isNull();
        var created = objectMapper.readTree(firstResponse.getContentAsString());
        assertThat(created.path("transferId").asText()).isEqualTo(transferId.toString());
        assertThat(created.path("status").asText()).isEqualTo("PENDING");
        assertThat(created.path("actions")).hasSize(1);
        assertThat(created.path("actions").get(0).path("type").asText()).isEqualTo("REQUEST_ACCOUNT_RESERVATION");

        assertThat(workflowRepository.findById(transferId))
                .hasValueSatisfying(transfer -> assertThat(transfer.correlationId()).isEqualTo("api-correlation-" + scenarioId));

        var replayResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                    "/internal/v1/transfer-workflows")
                        .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.AUTHORIZED_AUTHORIZATION)
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
                                                scenarioId,
                                                scenarioId,
                                                scenarioId,
                                                scenarioId)))
                .andReturn()
                .getResponse();

        assertThat(replayResponse.getStatus()).isEqualTo(200);
        assertThat(replayResponse.getHeader("Idempotent-Replay")).isEqualTo("true");
        assertThat(replayResponse.getHeader("Location")).isNull();
        var replay = objectMapper.readTree(replayResponse.getContentAsString());
        assertThat(replay.path("transferId").asText()).isEqualTo(transferId.toString());
        assertThat(replay.path("actions")).isEmpty();
    }

    @Test
    void mapsH2KnownWorkflowIdentityConflictToConflict() throws Exception {
        var scenarioId = UUID.randomUUID();
        var firstTransferId = UUID.randomUUID();
        var secondTransferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        var correlationId = "api-correlation-conflict-" + scenarioId;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.AUTHORIZED_AUTHORIZATION)
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 25.25,
                                  "currency": "AED",
                                  "correlationId": "%s",
                                  "transferRequestId": "api-transfer-request-%s",
                                  "reservationRequestId": "api-reservation-request-%s",
                                  "postingRequestId": "api-posting-request-%s"
                                }
                                """
                                        .formatted(
                                                firstTransferId,
                                                sourceAccountId,
                                                destinationAccountId,
                                                correlationId,
                                                scenarioId,
                                                scenarioId,
                                                scenarioId)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated());

        var conflictResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.AUTHORIZED_AUTHORIZATION)
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 30.50,
                                  "currency": "AED",
                                  "correlationId": "%s",
                                  "transferRequestId": "api-transfer-request-conflict-%s",
                                  "reservationRequestId": "api-reservation-request-conflict-%s",
                                  "postingRequestId": "api-posting-request-conflict-%s"
                                }
                                """
                                        .formatted(
                                                secondTransferId,
                                                sourceAccountId,
                                                destinationAccountId,
                                                correlationId,
                                                scenarioId,
                                                scenarioId,
                                                scenarioId)))
                .andReturn()
                .getResponse();

        assertThat(conflictResponse.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(conflictResponse.getContentAsString()).path("detail").asText())
                .isEqualTo("Transfer workflow request conflicts with existing data");
    }

    @Test
    void rejectsUnauthenticatedRequestWithoutCreatingWorkflow() throws Exception {
        var transferId = UUID.randomUUID();

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(requestBody(transferId)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(objectMapper.readTree(response.getContentAsString()).path("type").asText())
                .isEqualTo("urn:digital-bank:transaction:authentication-required");
        assertThat(workflowRepository.findById(transferId)).isEmpty();
    }

    @Test
    void rejectsAuthenticatedRequestFromUnapprovedSubjectWithoutCreatingWorkflow() throws Exception {
        var transferId = UUID.randomUUID();

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.UNAUTHORIZED_SUBJECT_AUTHORIZATION)
                        .contentType(APPLICATION_JSON)
                        .content(requestBody(transferId)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.getContentAsString()).path("type").asText())
                .isEqualTo("urn:digital-bank:transaction:access-denied");
        assertThat(workflowRepository.findById(transferId)).isEmpty();
    }

    @Test
    void rejectsAuthenticatedRequestWithoutTransferScopeWithoutCreatingWorkflow() throws Exception {
        var transferId = UUID.randomUUID();

        var response = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/internal/v1/transfer-workflows")
                        .header(HttpHeaders.AUTHORIZATION, TestSecurityConfig.INSUFFICIENT_SCOPE_AUTHORIZATION)
                        .contentType(APPLICATION_JSON)
                        .content(requestBody(transferId)))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(workflowRepository.findById(transferId)).isEmpty();
    }

    private static String requestBody(UUID transferId) {
        return """
                {
                  "transferId": "%s",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": 25.25,
                  "currency": "AED",
                  "correlationId": "security-correlation-%s",
                  "transferRequestId": "security-transfer-request-%s",
                  "reservationRequestId": "security-reservation-request-%s",
                  "postingRequestId": "security-posting-request-%s"
                }
                """.formatted(
                transferId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                transferId,
                transferId,
                transferId,
                transferId);
    }

    @Test
    @Transactional
    void getsPersistedTransferWorkflowThroughHttp() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        var transfer = Transfer.request(
                transferId,
                sourceAccountId,
                destinationAccountId,
                new BigDecimal("42.50"),
                "AED",
                "repository-lookup-correlation",
                "repository-lookup-transfer-request",
                "repository-lookup-reservation-request",
                "repository-lookup-posting-request");

        assertThat(workflowRepository.createIfAbsent(transfer)).isTrue();

        mockMvc.perform(get("/internal/v1/transfer-workflows/{transferId}", transferId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.transferId").value(transferId.toString()))
                .andExpect(jsonPath("$.sourceAccountId").value(sourceAccountId.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(destinationAccountId.toString()))
                .andExpect(jsonPath("$.amount").value(42.50))
                .andExpect(jsonPath("$.currency").value("AED"))
                .andExpect(jsonPath("$.correlationId").value("repository-lookup-correlation"))
                .andExpect(jsonPath("$.transferRequestId").value("repository-lookup-transfer-request"))
                .andExpect(jsonPath("$.reservationRequestId").value("repository-lookup-reservation-request"))
                .andExpect(jsonPath("$.postingRequestId").value("repository-lookup-posting-request"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.actions").isEmpty());
    }
}
