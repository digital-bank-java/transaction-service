package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable delivery boundary for terminal transfer workflow facts. */
public interface TransferTerminalEventOutbox {

    boolean recordIfAbsent(TransferTerminalEvent event);

    List<TransferTerminalEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil);

    void markPublished(TransferTerminalEvent event, UUID claimToken, Instant publishedAt);

    void markFailed(TransferTerminalEvent event, UUID claimToken, String error, Instant retryAt);
}
