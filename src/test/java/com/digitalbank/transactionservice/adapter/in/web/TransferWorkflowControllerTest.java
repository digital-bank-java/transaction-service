package com.digitalbank.transactionservice.adapter.in.web;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import com.digitalbank.transactionservice.domain.Transfer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class TransferWorkflowControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new TransferWorkflowController(new TransferProcessManager(
                new InMemoryWorkflowRepository(), new InMemoryWorkflowEventInbox(), new InMemoryWorkflowActionRepository()));
        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void postsTransferWorkflowCommand() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 15.75,
                                  "currency": "AED",
                                  "correlationId": "transfer-correlation-001",
                                  "transferRequestId": "transfer-request-001",
                                  "reservationRequestId": "reservation-request-001",
                                  "postingRequestId": "posting-request-001"
                                }
                                """
                                        .formatted(transferId, sourceAccountId, destinationAccountId)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.transferId").value(transferId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.actions[0].type").value("REQUEST_ACCOUNT_RESERVATION"))
                .andExpect(jsonPath("$.actions[0].reservationRequestId").value("reservation-request-001"));
    }

    @Test
    void getsExistingTransferWorkflowByTransferId() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 15.75,
                                  "currency": "AED",
                                  "correlationId": "transfer-correlation-lookup",
                                  "transferRequestId": "transfer-request-lookup",
                                  "reservationRequestId": "reservation-request-lookup",
                                  "postingRequestId": "posting-request-lookup"
                                }
                                """
                                        .formatted(transferId, sourceAccountId, destinationAccountId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/internal/v1/transfer-workflows/{transferId}", transferId))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.transferId").value(transferId.toString()))
                .andExpect(jsonPath("$.sourceAccountId").value(sourceAccountId.toString()))
                .andExpect(jsonPath("$.destinationAccountId").value(destinationAccountId.toString()))
                .andExpect(jsonPath("$.amount").value(15.75))
                .andExpect(jsonPath("$.currency").value("AED"))
                .andExpect(jsonPath("$.correlationId").value("transfer-correlation-lookup"))
                .andExpect(jsonPath("$.transferRequestId").value("transfer-request-lookup"))
                .andExpect(jsonPath("$.reservationRequestId").value("reservation-request-lookup"))
                .andExpect(jsonPath("$.postingRequestId").value("posting-request-lookup"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    @Test
    void returnsProblemDetailsWhenTransferWorkflowIsNotFound() throws Exception {
        var transferId = UUID.randomUUID();

        mockMvc.perform(get("/internal/v1/transfer-workflows/{transferId}", transferId))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://digital-bank-java.local/problems/transfer-workflow-not-found"))
                .andExpect(jsonPath("$.title").value("Transfer workflow not found"))
                .andExpect(jsonPath("$.detail").value("Transfer workflow not found: " + transferId));
    }

    @Test
    void returnsProblemDetailsForValidationFailure() throws Exception {
        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 0,
                                  "currency": "AE",
                                  "correlationId": "",
                                  "transferRequestId": "transfer-request-001",
                                  "reservationRequestId": "reservation-request-001",
                                  "postingRequestId": "posting-request-001"
                                }
                                """
                                        .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://digital-bank-java.local/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsAmountWithMoreThanFourFractionDigits() throws Exception {
        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 15.12345,
                                  "currency": "AED",
                                  "correlationId": "transfer-correlation-001",
                                  "transferRequestId": "transfer-request-001",
                                  "reservationRequestId": "reservation-request-001",
                                  "postingRequestId": "posting-request-001"
                                }
                                """
                                        .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("amount"));
    }

    @Test
    void rejectsRequestIdentifiersLongerThanPersistenceBoundary() throws Exception {
        var tooLong = "x".repeat(101);

        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 15.75,
                                  "currency": "AED",
                                  "correlationId": "%s",
                                  "transferRequestId": "%s",
                                  "reservationRequestId": "%s",
                                  "postingRequestId": "%s"
                                }
                                """
                                        .formatted(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                tooLong,
                                                tooLong,
                                                tooLong,
                                                tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[?(@.field == 'correlationId')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field == 'transferRequestId')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field == 'reservationRequestId')]").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field == 'postingRequestId')]").isNotEmpty());
    }

    @Test
    void returnsActionableErrorForCrossFieldValidationFailure() throws Exception {
        var accountId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "%s",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 15.75,
                                  "currency": "AED",
                                  "correlationId": "transfer-correlation-001",
                                  "transferRequestId": "transfer-request-001",
                                  "reservationRequestId": "reservation-request-001",
                                  "postingRequestId": "posting-request-001"
                                }
                                """
                                        .formatted(UUID.randomUUID(), accountId, accountId)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("sourceAccountId,destinationAccountId"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("sourceAccountId and destinationAccountId must differ"));
    }

    @Test
    void returnsProblemDetailsForInvalidJsonFieldTypes() throws Exception {
        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "transferId": "not-a-uuid",
                                  "sourceAccountId": "%s",
                                  "destinationAccountId": "%s",
                                  "amount": 15.75,
                                  "currency": "AED",
                                  "correlationId": "transfer-correlation-001",
                                  "transferRequestId": "transfer-request-001",
                                  "reservationRequestId": "reservation-request-001",
                                  "postingRequestId": "posting-request-001"
                                }
                                """
                                        .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://digital-bank-java.local/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Malformed JSON request"))
                .andExpect(jsonPath("$.errors[0].field").value("transferId"));
    }

    @Test
    void returnsProblemDetailsForMalformedJsonSyntax() throws Exception {
        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content("{\"transferId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://digital-bank-java.local/problems/validation-error"))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("Malformed JSON request"))
                .andExpect(jsonPath("$.errors[0].field").value("$"));
    }

    @Test
    void returnsProblemDetailsForWorkflowConflicts() throws Exception {
        var transferId = UUID.randomUUID();
        var sourceAccountId = UUID.randomUUID();
        var destinationAccountId = UUID.randomUUID();
        var initialPayload = """
                {
                  "transferId": "%s",
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": 15.75,
                  "currency": "AED",
                  "correlationId": "transfer-correlation-001",
                  "transferRequestId": "transfer-request-001",
                  "reservationRequestId": "reservation-request-001",
                  "postingRequestId": "posting-request-001"
                }
                """.formatted(transferId, sourceAccountId, destinationAccountId);
        var changedPayload = initialPayload.replace("\"amount\": 15.75", "\"amount\": 16.75");

        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(initialPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/internal/v1/transfer-workflows")
                        .contentType(APPLICATION_JSON)
                        .content(changedPayload))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://digital-bank-java.local/problems/transfer-workflow-conflict"))
                .andExpect(jsonPath("$.title").value("Transfer workflow conflict"))
                .andExpect(jsonPath("$.detail").value("transfer request conflicts with existing workflow"));
    }

    private static final class InMemoryWorkflowRepository implements TransferWorkflowRepository {

        private final java.util.Map<UUID, Transfer> values = new LinkedHashMap<>();

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

    private static final class InMemoryWorkflowEventInbox implements WorkflowEventInbox {

        @Override
        public Optional<WorkflowEventRecord> findByEventId(String eventId) {
            return Optional.empty();
        }

        @Override
        public void defer(WorkflowEventRecord event) {}

        @Override
        public void recordProcessed(WorkflowEventRecord event) {}

        @Override
        public void markProcessed(String eventId) {}

        @Override
        public List<WorkflowEventRecord> findDeferredByTransferId(UUID transferId) {
            return List.of();
        }
    }

    private static final class InMemoryWorkflowActionRepository implements WorkflowActionRepository {

        private final List<WorkflowAction> values = new ArrayList<>();

        @Override
        public boolean recordIfAbsent(WorkflowAction action) {
            var exists = values.stream().anyMatch(existing -> existing.actionId().equals(action.actionId()));
            if (exists) {
                return false;
            }
            values.add(action);
            return true;
        }

        @Override
        public long countByTransferId(UUID transferId) {
            return values.stream().filter(action -> action.transferId().equals(transferId)).count();
        }
    }
}
