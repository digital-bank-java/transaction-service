package com.digitalbank.transactionservice.adapter.in.messaging;

final class ReservationEventValidationException extends IllegalArgumentException {

    ReservationEventValidationException(String message) {
        super(message);
    }

    ReservationEventValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
