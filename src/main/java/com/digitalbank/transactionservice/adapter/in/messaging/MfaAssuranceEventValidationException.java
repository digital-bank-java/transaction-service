package com.digitalbank.transactionservice.adapter.in.messaging;

final class MfaAssuranceEventValidationException extends IllegalArgumentException {

    MfaAssuranceEventValidationException(String message) {
        super(message);
    }

    MfaAssuranceEventValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
