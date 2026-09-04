package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Internal Transfer Workflows")
class TransferWorkflowController {

    private static final String VALIDATION_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "errors": [
                {
                  "field": "currency",
                  "message": "must contain exactly 3 letters"
                }
              ]
            }
            """;

    private static final String MALFORMED_JSON_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Malformed JSON request",
              "errors": [
                {
                  "field": "transferId",
                  "message": "must be a valid UUID"
                }
              ]
            }
            """;

    private static final String CONFLICT_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/transfer-workflow-conflict",
              "title": "Transfer workflow conflict",
              "status": 409,
              "detail": "Transfer workflow request conflicts with existing data"
            }
            """;

    private static final String INVALID_TRANSFER_ID_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/validation-error",
              "title": "Invalid request",
              "status": 400,
              "detail": "Request validation failed",
              "errors": [
                {
                  "field": "transferId",
                  "message": "must be a valid UUID"
                }
              ]
            }
            """;

    private static final String NOT_FOUND_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/transfer-workflow-not-found",
              "title": "Transfer workflow not found",
              "status": 404,
              "detail": "Transfer workflow not found: 11111111-1111-1111-1111-111111111111"
            }
            """;

    private final TransferProcessManager processManager;

    TransferWorkflowController(TransferProcessManager processManager) {
        this.processManager = processManager;
    }

    @PostMapping("/internal/v1/transfer-workflows")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
            summary = "Request an internal transfer workflow",
            description = "Internal workflow-only endpoint. This starts or replays transfer orchestration state and is not a public customer-facing balance mutation API. Requires a bearer JWT with the transfer.internal scope and an allowlisted subject configured by the platform.")
    @ApiResponse(
            responseCode = "201",
            description = "Transfer workflow created",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TransferWorkflowResponse.class)))
    @ApiResponse(
            responseCode = "200",
            description = "Idempotent replay of an existing transfer workflow request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TransferWorkflowResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid workflow request or malformed JSON payload",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    {
                                        @ExampleObject(
                                                name = "validation-error",
                                                summary = "Validation failure",
                                                value = VALIDATION_PROBLEM_EXAMPLE),
                                        @ExampleObject(
                                                name = "malformed-json",
                                                summary = "Malformed JSON payload",
                                                value = MALFORMED_JSON_PROBLEM_EXAMPLE)
                                    }))
    @ApiResponse(
            responseCode = "409",
            description = "Workflow request conflicts with existing transfer state",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "workflow-conflict",
                                            summary = "Workflow conflict",
                                            value = CONFLICT_PROBLEM_EXAMPLE)))
    @ApiResponse(responseCode = "401", description = "Bearer authentication is required")
    @ApiResponse(responseCode = "403", description = "The authenticated subject is not authorized for transfer workflows")
    ResponseEntity<TransferWorkflowResponse> requestTransferWorkflow(
            @Valid @RequestBody InternalTransferWorkflowRequest request,
            Authentication authentication) {
        var result = processManager.requestTransfer(request.toCommand(
                authentication == null ? "system" : authentication.getName()));
        var response = TransferWorkflowResponse.from(result);
        var replay = !result.created();
        var status = replay ? HttpStatus.OK : HttpStatus.CREATED;
        var builder = ResponseEntity.status(status);
        if (replay) {
            builder.header("Idempotent-Replay", "true");
        }
        return builder.body(response);
    }

    @GetMapping("/internal/v1/transfer-workflows/{transferId}")
    @Operation(
            summary = "Get an internal transfer workflow",
            description = "Internal workflow-only endpoint. This retrieves existing transfer orchestration state by transfer ID and does not mutate account balances or ledger postings.")
    @ApiResponse(
            responseCode = "200",
            description = "Transfer workflow found",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TransferWorkflowResponse.class)))
    @ApiResponse(
            responseCode = "400",
            description = "Invalid transfer ID",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "invalid-transfer-id",
                                            summary = "Invalid transfer ID",
                                            value = INVALID_TRANSFER_ID_PROBLEM_EXAMPLE)))
    @ApiResponse(
            responseCode = "404",
            description = "Transfer workflow not found",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "workflow-not-found",
                                            summary = "Workflow not found",
                                            value = NOT_FOUND_PROBLEM_EXAMPLE)))
    ResponseEntity<TransferWorkflowResponse> getTransferWorkflow(@PathVariable UUID transferId) {
        return processManager.findTransferWorkflow(transferId)
                .map(TransferWorkflowResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new TransferWorkflowNotFoundException(transferId));
    }
}
