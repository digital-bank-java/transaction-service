package com.digitalbank.transactionservice.domain;

import java.util.Objects;
import java.util.UUID;

public final class Transfer {

    private final UUID id;
    private TransferStatus status;

    private Transfer(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = TransferStatus.PENDING;
    }

    public static Transfer pending(UUID id) {
        return new Transfer(id);
    }

    public UUID id() {
        return id;
    }

    public TransferStatus status() {
        return status;
    }

    public void complete() {
        transitionTo(TransferStatus.COMPLETED);
    }

    public void fail() {
        transitionTo(TransferStatus.FAILED);
    }

    public void reverse() {
        transitionTo(TransferStatus.REVERSED);
    }

    private void transitionTo(TransferStatus target) {
        if (status == target) {
            return;
        }

        boolean allowed = (status == TransferStatus.PENDING
                && (target == TransferStatus.COMPLETED || target == TransferStatus.FAILED))
                || (status == TransferStatus.COMPLETED && target == TransferStatus.REVERSED);

        if (!allowed) {
            throw new IllegalTransferTransitionException(
                    "Cannot transition transfer from " + status + " to " + target);
        }

        status = target;
    }
}
