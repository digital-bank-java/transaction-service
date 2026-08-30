package com.digitalbank.transactionservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TransferTest {

    private static final UUID TRANSFER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void pendingTransferCanBeCompleted() {
        var transfer = Transfer.pending(TRANSFER_ID);

        transfer.complete();

        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void pendingTransferCanFail() {
        var transfer = Transfer.pending(TRANSFER_ID);

        transfer.fail();

        assertThat(transfer.status()).isEqualTo(TransferStatus.FAILED);
    }

    @Test
    void completedTransferCanBeReversed() {
        var transfer = Transfer.pending(TRANSFER_ID);
        transfer.complete();

        transfer.reverse();

        assertThat(transfer.status()).isEqualTo(TransferStatus.REVERSED);
    }

    @Test
    void illegalTransitionIsRejected() {
        var transfer = Transfer.pending(TRANSFER_ID);
        transfer.fail();

        assertThatThrownBy(transfer::complete)
                .isInstanceOf(IllegalTransferTransitionException.class)
                .hasMessage("Cannot transition transfer from FAILED to COMPLETED");
    }

    @Test
    void repeatedTerminalEventIsIdempotent() {
        var transfer = Transfer.pending(TRANSFER_ID);
        transfer.complete();

        transfer.complete();

        assertThat(transfer.status()).isEqualTo(TransferStatus.COMPLETED);
    }
}
