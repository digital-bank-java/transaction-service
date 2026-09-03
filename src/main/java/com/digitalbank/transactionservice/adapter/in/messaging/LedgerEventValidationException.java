package com.digitalbank.transactionservice.adapter.in.messaging;

final class LedgerEventValidationException extends IllegalArgumentException {

    LedgerEventValidationException(String message) {
        super(message);
    }

    LedgerEventValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
