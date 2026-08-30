package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.service.TransferProcessManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    private static final String CONFLICT_PROBLEM_EXAMPLE = """
            {
              "type": "https://digital-bank-java.local/problems/transfer-workflow-conflict",
              "title": "Transfer workflow conflict",
              "status": 409,
              "detail": "transfer request conflicts with existing workflow"
            }
            """;

    private final TransferProcessManager processManager;

    TransferWorkflowController(TransferProcessManager processManager) {
        this.processManager = processManager;
    }

    @PostMapping("/internal/v1/transfer-workflows")
    @Operation(
            summary = "Request an internal transfer workflow",
            description = "Internal workflow-only endpoint. This starts or replays transfer orchestration state and is not a public customer-facing balance mutation API.")
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
            description = "Invalid workflow request",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            schema = @Schema(implementation = ProblemDetail.class),
                            examples =
                                    @ExampleObject(
                                            name = "validation-error",
                                            summary = "Validation failure",
                                            value = VALIDATION_PROBLEM_EXAMPLE)))
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
    ResponseEntity<TransferWorkflowResponse> requestTransferWorkflow(
            @Valid @RequestBody InternalTransferWorkflowRequest request) {
        var result = processManager.requestTransfer(request.toCommand());
        var response = TransferWorkflowResponse.from(result);
        var replay = result.actions().isEmpty();
        var status = replay ? HttpStatus.OK : HttpStatus.CREATED;
        var builder = ResponseEntity.status(status);
        if (replay) {
            builder.header("Idempotent-Replay", "true");
        }
        return builder.location(URI.create("/internal/v1/transfer-workflows/" + response.transferId()))
                .body(response);
    }
}
