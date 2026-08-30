package com.digitalbank.transactionservice.adapter.in.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.digitalbank.transactionservice.domain.IllegalTransferTransitionException;
import com.digitalbank.transactionservice.domain.TransferConflictException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
class ApiExceptionHandler {

    private static final Pattern REFERENCE_CHAIN_FIELD = Pattern.compile("\\[\"([^\"]+)\"\\]");
    private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint [\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern H2_WORKFLOW_IDENTITY_INDEX = Pattern.compile(
            "unique index or primary key violation: \"(?:[^\".]+\\.)?"
                    + "(?:pk_transfer_workflows|uq_transfer_workflows_"
                    + "(?:correlation_id|transfer_request_id|reservation_request_id|posting_request_id))"
                    + "(?:_index_[a-z0-9]+)?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> WORKFLOW_IDENTITY_CONSTRAINTS = Set.of(
            "pk_transfer_workflows",
            "uq_transfer_workflows_correlation_id",
            "uq_transfer_workflows_transfer_request_id",
            "uq_transfer_workflows_reservation_request_id",
            "uq_transfer_workflows_posting_request_id");

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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleMessageNotReadable(HttpMessageNotReadableException exception) {
        return badRequestProblem("Malformed JSON request", describeMessageNotReadable(exception));
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return badRequestProblem(
                "Request validation failed",
                Map.of(
                        "field", exception.getName(),
                        "message", invalidFormatMessage(exception.getRequiredType())));
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
        IllegalTransferTransitionException.class
    })
    ResponseEntity<ProblemDetail> handleWorkflowConflict(RuntimeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle("Transfer workflow conflict");
        problem.setType(URI.create("https://digital-bank-java.local/problems/transfer-workflow-conflict"));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        if (isKnownWorkflowIdentityConflict(exception)) {
            var problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.CONFLICT, "Transfer workflow request conflicts with existing data");
            problem.setTitle("Transfer workflow conflict");
            problem.setType(URI.create("https://digital-bank-java.local/problems/transfer-workflow-conflict"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
        }

        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "The request could not be completed");
        problem.setTitle("Internal server error");
        problem.setType(URI.create("https://digital-bank-java.local/problems/internal-error"));
        return ResponseEntity.internalServerError().body(problem);
    }

    private static boolean isKnownWorkflowIdentityConflict(DataIntegrityViolationException exception) {
        var message = allMessages(exception);
        var matcher = CONSTRAINT_NAME.matcher(message);
        return (matcher.find() && WORKFLOW_IDENTITY_CONSTRAINTS.contains(matcher.group(1)))
                || H2_WORKFLOW_IDENTITY_INDEX.matcher(message).find();
    }

    private static ResponseEntity<ProblemDetail> badRequestProblem(String detail, Map<String, String> error) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", java.util.List.of(error));
        return ResponseEntity.badRequest().body(problem);
    }

    private static Map<String, String> describeMessageNotReadable(HttpMessageNotReadableException exception) {
        var invalidFormatException = findCause(exception, InvalidFormatException.class);
        if (invalidFormatException != null) {
            return Map.of(
                    "field", jsonFieldPath(invalidFormatException),
                    "message", invalidFormatMessage(invalidFormatException.getTargetType()));
        }
        var mappingException = findCause(exception, JsonMappingException.class);
        if (mappingException != null) {
            var field = jsonFieldPath(mappingException);
            return Map.of(
                    "field", "$".equals(field) ? extractJsonField(allMessages(exception)) : field,
                    "message", jsonMappingMessage(mappingException));
        }
        if (findCause(exception, JsonParseException.class) != null) {
            return Map.of("field", "$", "message", "request body is not valid JSON");
        }
        var field = extractJsonField(allMessages(exception));
        return Map.of("field", field, "message", "request body is unreadable");
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

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        var current = exception;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private static String jsonFieldPath(JsonMappingException exception) {
        if (exception.getPath().isEmpty()) {
            return "$";
        }
        return exception.getPath().stream()
                .map(reference -> {
                    if (reference.getFieldName() != null) {
                        return reference.getFieldName();
                    }
                    return "[" + reference.getIndex() + "]";
                })
                .reduce((left, right) -> left + "." + right)
                .orElse("$")
                .replace(".[", "[");
    }

    private static String jsonMappingMessage(JsonMappingException exception) {
        var message = exception.getOriginalMessage();
        if (message != null && message.contains("UUID")) {
            return "must be a valid UUID";
        }
        if (message != null && message.contains("BigDecimal")) {
            return "must be a valid number";
        }
        return "value is invalid";
    }

    private static String invalidFormatMessage(Class<?> targetType) {
        if (targetType == null) {
            return "value is invalid";
        }
        if (UUID.class.isAssignableFrom(targetType)) {
            return "must be a valid UUID";
        }
        if (BigDecimal.class.isAssignableFrom(targetType)
                || Number.class.isAssignableFrom(targetType)
                || targetType.isPrimitive()) {
            return "must be a valid number";
        }
        return "value is invalid";
    }

    private static String extractJsonField(String message) {
        if (message == null) {
            return "$";
        }
        var matcher = REFERENCE_CHAIN_FIELD.matcher(message);
        String field = null;
        while (matcher.find()) {
            field = matcher.group(1);
        }
        return field == null ? "$" : field;
    }

    private static String allMessages(Throwable exception) {
        var messages = new StringBuilder();
        var current = exception;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (!messages.isEmpty()) {
                    messages.append('\n');
                }
                messages.append(current.getMessage());
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return messages.toString();
    }
}
