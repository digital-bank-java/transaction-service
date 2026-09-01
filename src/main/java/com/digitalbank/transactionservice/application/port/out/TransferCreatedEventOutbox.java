package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TransferCreatedEventOutbox {

    boolean recordIfAbsent(TransferCreatedEvent event);

    default List<TransferCreatedEvent> findReady(int limit, Instant now) {
        return List.of();
    }

    /**
     * Claims ready events for one publisher worker until the lease expires.
     * Production adapters must implement this atomically.
     */
    default List<TransferCreatedEvent> claimReady(
            int limit, Instant now, UUID claimToken, Instant leaseUntil) {
        return findReady(limit, now);
    }

    default void markPublished(TransferCreatedEvent event, Instant publishedAt) {}

    default void markPublished(TransferCreatedEvent event, UUID claimToken, Instant publishedAt) {
        markPublished(event, publishedAt);
    }

    default void markFailed(TransferCreatedEvent event, String error, Instant retryAt) {}

    default void markFailed(
            TransferCreatedEvent event, UUID claimToken, String error, Instant retryAt) {
        markFailed(event, error, retryAt);
    }
}
