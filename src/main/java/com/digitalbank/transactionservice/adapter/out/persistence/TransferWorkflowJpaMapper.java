package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.domain.Transfer;
import java.time.Instant;

final class TransferWorkflowJpaMapper {

    private TransferWorkflowJpaMapper() {}

    static TransferWorkflowJpaEntity newEntity(Transfer transfer) {
        return new TransferWorkflowJpaEntity(
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount(),
                transfer.currency(),
                transfer.correlationId(),
                transfer.transferRequestId(),
                transfer.reservationRequestId(),
                transfer.postingRequestId(),
                transfer.reservationId(),
                transfer.status(),
                Instant.now());
    }

    static Transfer toDomain(TransferWorkflowJpaEntity entity) {
        return Transfer.rehydrate(
                entity.id(),
                entity.sourceAccountId(),
                entity.destinationAccountId(),
                entity.amount(),
                entity.currency(),
                entity.correlationId(),
                entity.transferRequestId(),
                entity.reservationRequestId(),
                entity.postingRequestId(),
                entity.reservationId(),
                entity.status(),
                entity.version());
    }
}
