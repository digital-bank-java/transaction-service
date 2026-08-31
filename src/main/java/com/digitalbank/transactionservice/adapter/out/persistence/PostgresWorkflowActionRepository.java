package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.application.port.out.WorkflowActionRepository;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.stereotype.Repository;

@Repository
class PostgresWorkflowActionRepository implements WorkflowActionRepository {

    private final SpringDataWorkflowActionRepository repository;
    private final boolean h2;

    PostgresWorkflowActionRepository(SpringDataWorkflowActionRepository repository, DataSource dataSource) {
        this.repository = repository;
        this.h2 = databaseIsH2(dataSource);
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

    private static boolean databaseIsH2(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not determine database product", exception);
        }
    }
}
