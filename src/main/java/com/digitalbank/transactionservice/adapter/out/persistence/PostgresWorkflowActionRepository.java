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
        if (repository.existsById(action.actionId())) {
            return false;
        }
        repository.saveAndFlush(new WorkflowActionJpaEntity(action));
        return true;
    }

    @Override
    public long countByTransferId(UUID transferId) {
        return repository.countByTransferId(transferId);
    }
}
