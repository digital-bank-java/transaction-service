package com.digitalbank.transactionservice.domain;

public enum TransferStatus {
    AWAITING_STEP_UP,
    PENDING,
    AWAITING_LEDGER_POSTING,
    AWAITING_RESERVATION_RELEASE,
    COMPLETED,
    FAILED,
    REVERSED
}
