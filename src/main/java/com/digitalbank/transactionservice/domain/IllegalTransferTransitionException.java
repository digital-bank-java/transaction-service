package com.digitalbank.transactionservice.domain;

public class IllegalTransferTransitionException extends IllegalStateException {

    public IllegalTransferTransitionException(String message) {
        super(message);
    }
}
