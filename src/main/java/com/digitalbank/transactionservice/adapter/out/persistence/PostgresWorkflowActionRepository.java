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
        var values = WorkflowActionJpaEntity.values(action);
        var recorded = (h2 ? repository.recordIfAbsentH2(
                        values.actionId(),
                        values.transferId(),
                        values.actionType(),
                        values.correlationId(),
                        values.sourceAccountId(),
                        values.destinationAccountId(),
                        values.amount(),
                        values.currency(),
                        values.requestId(),
                        values.reservationRequestId(),
                        values.postingRequestId(),
                        values.reservationId()) : repository.recordIfAbsent(
                        values.actionId(),
                        values.transferId(),
                        values.actionType(),
                        values.correlationId(),
                        values.sourceAccountId(),
                        values.destinationAccountId(),
                        values.amount(),
                        values.currency(),
                        values.requestId(),
                        values.reservationRequestId(),
                        values.postingRequestId(),
                        values.reservationId()));
        return recorded == 1;
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
