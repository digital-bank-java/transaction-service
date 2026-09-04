package com.digitalbank.transactionservice.adapter.in.web;

import com.digitalbank.transactionservice.application.port.in.RequestTransferCommand;
import com.digitalbank.transactionservice.risk.TransferDestinationClass;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

record InternalTransferWorkflowRequest(
        @NotNull UUID transferId,
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull
                @Positive
                @Digits(
                        integer = 15,
                        fraction = 4,
                        message = "must have up to 15 integer digits and 4 fraction digits")
                BigDecimal amount,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must contain exactly 3 letters") String currency,
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String correlationId,
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String transferRequestId,
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String reservationRequestId,
        @NotBlank @Size(max = 100, message = "must be at most 100 characters") String postingRequestId,
        @Size(max = 100, message = "must be at most 100 characters") String decisionRequestId,
        @Size(max = 30, message = "must be at most 30 characters") String channel,
        TransferDestinationClass destinationClass) {

    @AssertTrue(message = "sourceAccountId and destinationAccountId must differ")
    boolean hasDistinctAccounts() {
        return sourceAccountId == null
                || destinationAccountId == null
                || !sourceAccountId.equals(destinationAccountId);
    }

    RequestTransferCommand toCommand(String authenticatedSubject) {
        return new RequestTransferCommand(
                transferId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                correlationId,
                transferRequestId,
                reservationRequestId,
                postingRequestId,
                authenticatedSubject,
                channel == null || channel.isBlank() ? "INTERNAL" : channel,
                destinationClass == null ? TransferDestinationClass.INTERNAL : destinationClass,
                decisionRequestId == null || decisionRequestId.isBlank() ? transferRequestId : decisionRequestId);
    }
}
