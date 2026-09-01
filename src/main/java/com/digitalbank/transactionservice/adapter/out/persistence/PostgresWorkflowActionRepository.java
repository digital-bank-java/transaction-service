package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PostgresWorkflowActionRepository implements WorkflowActionRepository {

    private final SpringDataWorkflowActionRepository repository;

    PostgresWorkflowActionRepository(SpringDataWorkflowActionRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean recordIfAbsent(WorkflowAction action) {
        var entity = new WorkflowActionJpaEntity(action);
        return repository.insertIfAbsent(
                entity.actionId(),
                entity.transferId(),
                entity.actionType(),
                entity.correlationId(),
                entity.sourceAccountId(),
                entity.destinationAccountId(),
                entity.amount(),
                entity.currency(),
                entity.requestId(),
                entity.reservationRequestId(),
                entity.postingRequestId(),
                entity.reservationId()) == 1;
    }

    @Override
    public long countByTransferId(UUID transferId) {
        return repository.countByTransferId(transferId);
    }
}
