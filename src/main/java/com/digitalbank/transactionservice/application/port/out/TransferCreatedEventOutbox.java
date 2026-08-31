package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.List;

public interface TransferCreatedEventOutbox {

    boolean recordIfAbsent(TransferCreatedEvent event);

    default List<TransferCreatedEvent> findReady(int limit, Instant now) {
        return List.of();
    }

    default void markPublished(TransferCreatedEvent event, Instant publishedAt) {}

    default void markFailed(TransferCreatedEvent event, String error, Instant retryAt) {}
}
