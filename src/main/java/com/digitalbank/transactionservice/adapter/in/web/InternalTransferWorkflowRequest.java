package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

record InternalTransferWorkflowRequest(
        @NotNull UUID transferId,
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @Positive BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must contain exactly 3 letters") String currency,
        @NotBlank String correlationId,
        @NotBlank String transferRequestId,
        @NotBlank String reservationRequestId,
        @NotBlank String postingRequestId) {

    @AssertTrue(message = "sourceAccountId and destinationAccountId must differ")
    boolean hasDistinctAccounts() {
        return sourceAccountId == null
                || destinationAccountId == null
                || !sourceAccountId.equals(destinationAccountId);
    }

    RequestTransferCommand toCommand() {
        return new RequestTransferCommand(
                transferId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                correlationId,
                transferRequestId,
                reservationRequestId,
                postingRequestId);
    }
}
