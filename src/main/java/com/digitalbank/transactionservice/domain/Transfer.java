package com.digitalbank.transactionservice.domain;

import java.util.Objects;
import java.util.UUID;
import java.math.BigDecimal;

public final class Transfer {

    private final UUID id;
    private final UUID sourceAccountId;
    private final UUID destinationAccountId;
    private final BigDecimal amount;
    private final String currency;
    private final String correlationId;
    private final String transferRequestId;
    private final String reservationRequestId;
    private final String postingRequestId;
    private TransferStatus status;
    private String reservationId;
    private long version;

    private Transfer(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId,
            String reservationId,
            TransferStatus status,
            long version) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.sourceAccountId = Objects.requireNonNull(sourceAccountId, "sourceAccountId must not be null");
        this.destinationAccountId = Objects.requireNonNull(destinationAccountId, "destinationAccountId must not be null");
        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("sourceAccountId and destinationAccountId must differ");
        }
        this.amount = requirePositive(amount);
        this.currency = requireCurrency(currency);
        this.correlationId = requireText(correlationId, "correlationId");
        this.transferRequestId = requireText(transferRequestId, "transferRequestId");
        this.reservationRequestId = requireText(reservationRequestId, "reservationRequestId");
        this.postingRequestId = requireText(postingRequestId, "postingRequestId");
        this.reservationId = reservationId == null ? null : requireText(reservationId, "reservationId");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (status == TransferStatus.AWAITING_LEDGER_POSTING && this.reservationId == null) {
            throw new IllegalArgumentException("awaiting ledger posting requires reservationId");
        }
        if (status == TransferStatus.AWAITING_RESERVATION_RELEASE && this.reservationId == null) {
            throw new IllegalArgumentException("awaiting reservation release requires reservationId");
        }
        if (status == TransferStatus.PENDING && this.reservationId != null) {
            throw new IllegalArgumentException("pending transfer must not have reservationId");
        }
        this.version = version;
    }

    public static Transfer request(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId) {
        return new Transfer(
                id,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                correlationId,
                transferRequestId,
                reservationRequestId,
                postingRequestId,
                null,
                TransferStatus.PENDING,
                0);
    }

    public static Transfer rehydrate(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            String currency,
            String correlationId,
            String transferRequestId,
            String reservationRequestId,
            String postingRequestId,
            String reservationId,
            TransferStatus status,
            long version) {
        return new Transfer(
                id,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                correlationId,
                transferRequestId,
                reservationRequestId,
                postingRequestId,
                reservationId,
                status,
                version);
    }

    public UUID id() {
        return id;
    }

    public UUID sourceAccountId() {
        return sourceAccountId;
    }

    public UUID destinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String currency() {
        return currency;
    }

    public String correlationId() {
        return correlationId;
    }

    public String transferRequestId() {
        return transferRequestId;
    }

    public String reservationRequestId() {
        return reservationRequestId;
    }

    public String reservationId() {
        return reservationId;
    }

    public String postingRequestId() {
        return postingRequestId;
    }

    public long version() {
        return version;
    }

    public TransferStatus status() {
        return status;
    }

    public boolean accountReservationCreated(
            String receivedReservationRequestId,
            String receivedReservationId,
            String receivedCorrelationId) {
        requireMatching(correlationId, receivedCorrelationId, "correlationId");
        requireMatching(reservationRequestId, receivedReservationRequestId, "reservationRequestId");
        var normalizedReservationId = requireText(receivedReservationId, "reservationId");

        if (reservationId != null) {
            if (Objects.equals(reservationId, normalizedReservationId)) {
                return false;
            }
            throw new TransferConflictException("reservationId does not match the existing transfer reservation");
        }
        transitionTo(TransferStatus.AWAITING_LEDGER_POSTING);
        reservationId = normalizedReservationId;
        version++;
        return true;
    }

    public boolean accountReservationRejected() {
        if (status == TransferStatus.FAILED) {
            return false;
        }
        transitionTo(TransferStatus.FAILED);
        version++;
        return true;
    }

    public boolean ledgerPostingCompleted(String receivedPostingRequestId, String receivedCorrelationId) {
        requireMatching(correlationId, receivedCorrelationId, "correlationId");
        requireMatching(postingRequestId, receivedPostingRequestId, "postingRequestId");
        if (status == TransferStatus.COMPLETED) {
            return false;
        }
        transitionTo(TransferStatus.COMPLETED);
        version++;
        return true;
    }

    public boolean ledgerPostingFailed(String receivedPostingRequestId, String receivedCorrelationId) {
        requireMatching(correlationId, receivedCorrelationId, "correlationId");
        requireMatching(postingRequestId, receivedPostingRequestId, "postingRequestId");
        if (status == TransferStatus.AWAITING_RESERVATION_RELEASE
                || status == TransferStatus.FAILED) {
            return false;
        }
        transitionTo(TransferStatus.AWAITING_RESERVATION_RELEASE);
        version++;
        return true;
    }

    public boolean accountReservationReleased(
            String receivedReservationRequestId,
            String receivedReservationId,
            String receivedCorrelationId) {
        requireMatching(correlationId, receivedCorrelationId, "correlationId");
        requireMatching(reservationRequestId, receivedReservationRequestId, "reservationRequestId");
        requireMatching(reservationId, receivedReservationId, "reservationId");
        if (status == TransferStatus.FAILED) {
            return false;
        }
        transitionTo(TransferStatus.FAILED);
        version++;
        return true;
    }

    public boolean accountReservationExpired(
            String receivedReservationRequestId,
            String receivedReservationId,
            String receivedCorrelationId) {
        requireMatching(correlationId, receivedCorrelationId, "correlationId");
        requireMatching(reservationRequestId, receivedReservationRequestId, "reservationRequestId");
        requireMatching(reservationId, receivedReservationId, "reservationId");
        if (status == TransferStatus.FAILED) {
            return false;
        }
        transitionTo(TransferStatus.FAILED);
        version++;
        return true;
    }

    public void reverse() {
        transitionTo(TransferStatus.REVERSED);
        version++;
    }

    private void transitionTo(TransferStatus target) {
        if (status == target) {
            return;
        }

        boolean allowed = (status == TransferStatus.PENDING
                && target == TransferStatus.AWAITING_LEDGER_POSTING)
                || (status == TransferStatus.PENDING && target == TransferStatus.FAILED)
                || (status == TransferStatus.AWAITING_LEDGER_POSTING
                        && (target == TransferStatus.COMPLETED || target == TransferStatus.AWAITING_RESERVATION_RELEASE))
                || (status == TransferStatus.AWAITING_RESERVATION_RELEASE
                        && target == TransferStatus.FAILED)
                || (status == TransferStatus.AWAITING_LEDGER_POSTING
                        && target == TransferStatus.FAILED)
                || (status == TransferStatus.COMPLETED && target == TransferStatus.REVERSED);

        if (!allowed) {
            throw new IllegalTransferTransitionException(
                    "Cannot transition transfer from " + status + " to " + target);
        }

        status = target;
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        Objects.requireNonNull(value, "amount must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return value;
    }

    private static String requireCurrency(String value) {
        var currency = requireText(value, "currency").toUpperCase();
        if (currency.length() != 3) {
            throw new IllegalArgumentException("currency must have length 3");
        }
        return currency;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static void requireMatching(String expected, String actual, String field) {
        if (!Objects.equals(expected, actual)) {
            throw new TransferConflictException(field + " does not match transfer workflow");
        }
    }
}
