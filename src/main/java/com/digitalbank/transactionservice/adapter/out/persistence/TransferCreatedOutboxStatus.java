package com.digitalbank.transactionservice.adapter.out.persistence;

enum TransferCreatedOutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
