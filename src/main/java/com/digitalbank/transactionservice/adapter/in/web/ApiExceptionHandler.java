package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.domain.IllegalTransferTransitionException;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationFailure(MethodArgumentNotValidException exception) {
        var errors = new ArrayList<Map<String, String>>();
        exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", normalizeField(error.getField()),
                        "message", String.valueOf(error.getDefaultMessage())))
                .forEach(errors::add);
        exception.getBindingResult().getGlobalErrors().stream()
                .map(ApiExceptionHandler::toGlobalError)
                .forEach(errors::add);

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException exception) {
        var errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({
        TransferConflictException.class,
        IllegalTransferTransitionException.class,
        DataIntegrityViolationException.class
    })
    ResponseEntity<ProblemDetail> handleWorkflowConflict(RuntimeException exception) {
        var detail = exception instanceof DataIntegrityViolationException
                ? "Transfer workflow request conflicts with existing data"
                : exception.getMessage();
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setTitle("Transfer workflow conflict");
        problem.setType(URI.create("https://digital-bank-java.local/problems/transfer-workflow-conflict"));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    private static Map<String, String> toGlobalError(ObjectError error) {
        return Map.of(
                "field",
                switch (String.valueOf(error.getCode())) {
                    case "AssertTrue" -> "sourceAccountId,destinationAccountId";
                    default -> "$";
                },
                "message",
                String.valueOf(error.getDefaultMessage()));
    }

    private static String normalizeField(String field) {
        return switch (field) {
            case "distinctAccounts" -> "sourceAccountId,destinationAccountId";
            default -> field;
        };
    }
}
