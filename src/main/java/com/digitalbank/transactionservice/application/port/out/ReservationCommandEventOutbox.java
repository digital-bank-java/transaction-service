package com.digitalbank.transactionservice.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservationCommandEventOutbox {

    boolean recordIfAbsent(ReservationCommandEvent event);

    List<ReservationCommandEvent> claimReady(int limit, Instant now, UUID claimToken, Instant leaseUntil);

    void markPublished(ReservationCommandEvent event, UUID claimToken, Instant publishedAt);

    void markFailed(ReservationCommandEvent event, UUID claimToken, String error, Instant retryAt);
}
