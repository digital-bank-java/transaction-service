package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.util.Optional;
import java.util.UUID;

public interface TransferWorkflowRepository {

    Optional<Transfer> findById(UUID transferId);

    /**
     * Creates the workflow only when its primary identity is not already present.
     * Persistence adapters must implement this atomically when backed by a database.
     */
    default boolean createIfAbsent(Transfer transfer) {
        if (findById(transfer.id()).isPresent()) {
            return false;
        }
        save(transfer);
        return true;
    }

    Transfer save(Transfer transfer);
}
