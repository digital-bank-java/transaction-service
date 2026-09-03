package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.WorkflowEventInbox;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PostgresWorkflowEventInbox implements WorkflowEventInbox {

    private final SpringDataWorkflowEventRepository repository;

    PostgresWorkflowEventInbox(SpringDataWorkflowEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<WorkflowEventRecord> findByEventId(String eventId) {
        return repository.findById(eventId).map(WorkflowEventJpaEntity::toRecord);
    }

    @Override
    public void defer(WorkflowEventRecord event) {
        repository.saveAndFlush(new WorkflowEventJpaEntity(event.deferred()));
    }

    @Override
    public void recordProcessed(WorkflowEventRecord event) {
        repository.saveAndFlush(new WorkflowEventJpaEntity(event.processed()));
    }

    @Override
    public void markProcessed(String eventId) {
        repository.findById(eventId).ifPresent(event -> {
            event.markProcessed();
            repository.saveAndFlush(event);
        });
    }

    @Override
    public List<WorkflowEventRecord> findDeferredByTransferId(UUID transferId) {
        return repository.findByTransferIdAndStatusOrderByCreatedAtAsc(transferId, EventStatus.DEFERRED).stream()
                .map(WorkflowEventJpaEntity::toRecord)
                .toList();
    }
}
