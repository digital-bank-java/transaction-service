package com.digitalbank.transactionservice.domain;

public enum TransferStatus {
    PENDING,
    AWAITING_LEDGER_POSTING,
    AWAITING_RESERVATION_RELEASE,
    COMPLETED,
    FAILED,
    REVERSED
}
