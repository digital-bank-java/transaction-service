package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerCommandEventOutbox {

    boolean recordIfAbsent(LedgerCommandEvent event);

    List<LedgerCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil);

    void markPublished(LedgerCommandEvent event, UUID claimToken, Instant publishedAt);

    void markFailed(LedgerCommandEvent event, UUID claimToken, String error, Instant retryAt);

    default Optional<String> payloadFor(LedgerCommandEvent event) {
        return Optional.empty();
    }
}
