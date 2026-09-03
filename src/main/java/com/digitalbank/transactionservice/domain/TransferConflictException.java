package com.digitalbank.transactionservice.domain;

public class TransferConflictException extends IllegalStateException {

    public TransferConflictException(String message) {
        super(message);
    }
}
