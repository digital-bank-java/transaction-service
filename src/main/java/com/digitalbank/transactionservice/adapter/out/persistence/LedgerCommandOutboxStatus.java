package com.digitalbank.transactionservice.adapter.out.persistence;

enum LedgerCommandOutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
