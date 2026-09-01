package com.digitalbank.transactionservice.domain;

public enum TransferStatus {
    PENDING,
    AWAITING_LEDGER_POSTING,
    COMPLETED,
    FAILED,
    REVERSED
}
