package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.TransferWorkflowRepository;
import com.digitalbank.transactionservice.domain.Transfer;
import jakarta.persistence.OptimisticLockException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class PostgresTransferWorkflowRepository implements TransferWorkflowRepository {

    private final SpringDataTransferWorkflowRepository repository;

    PostgresTransferWorkflowRepository(SpringDataTransferWorkflowRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Transfer> findById(UUID transferId) {
        return repository.findById(transferId).map(TransferWorkflowJpaMapper::toDomain);
    }

    @Override
    public Transfer saveIfAbsent(Transfer transfer) {
        repository.insertIfAbsent(
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
                transfer.status().name(),
                transfer.version());
        return findById(transfer.id())
                .orElseThrow(() -> new IllegalStateException("Transfer workflow was not persisted: " + transfer.id()));
    }

    @Override
    public Transfer save(Transfer transfer) {
        var entity = repository.findById(transfer.id()).orElse(null);
        if (entity == null) {
            return TransferWorkflowJpaMapper.toDomain(
                    repository.saveAndFlush(TransferWorkflowJpaMapper.newEntity(transfer)));
        }

        var expectedVersion = transfer.version() == 0 ? 0 : transfer.version() - 1;
        if (!Long.valueOf(expectedVersion).equals(entity.version())) {
            throw new OptimisticLockException("Transfer workflow version is stale: " + transfer.id());
        }
        entity.updateFrom(transfer, Instant.now());
        return TransferWorkflowJpaMapper.toDomain(repository.saveAndFlush(entity));
    }
}
