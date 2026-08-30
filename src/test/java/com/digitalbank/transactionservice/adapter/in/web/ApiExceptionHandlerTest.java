package com.digitalbank.transactionservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void serializesFieldAndGlobalValidationErrors() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "internalTransferWorkflowRequest");
        bindingResult.addError(new FieldError(
                "internalTransferWorkflowRequest", "currency", "must contain exactly 3 letters"));
        bindingResult.addError(new ObjectError(
                "internalTransferWorkflowRequest", "sourceAccountId and destinationAccountId must differ"));
        var exception = new MethodArgumentNotValidException(requestParameter(), bindingResult);

        var response = handler.handleValidationFailure(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        var errors = (List<Map<String, String>>) response.getBody().getProperties().get("errors");
        assertThat(errors).containsExactly(
                Map.of("field", "currency", "message", "must contain exactly 3 letters"),
                Map.of("field", "$", "message", "sourceAccountId and destinationAccountId must differ"));
    }

    @Test
    void mapsKnownWorkflowIdentityConstraintToConflict() {
        var exception = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_transfer_workflows_transfer_request_id\"");

        var response = handler.handleDataIntegrityViolation(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).isEqualTo("Transfer workflow request conflicts with existing data");
    }

    @Test
    void mapsUnexpectedDatabaseFailureToInternalServerError() {
        var response = handler.handleDataIntegrityViolation(new DataIntegrityViolationException("database unavailable"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getDetail()).isEqualTo("The request could not be completed");
    }

    private static MethodParameter requestParameter() throws NoSuchMethodException {
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("request", InternalTransferWorkflowRequest.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private static void request(InternalTransferWorkflowRequest request) {}
}
