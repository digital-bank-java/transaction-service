package com.digitalbank.transactionservice.application.port.out;

import com.digitalbank.transactionservice.domain.Transfer;
import java.util.Optional;
import java.util.UUID;

public interface TransferWorkflowRepository {

    Optional<Transfer> findById(UUID transferId);

    Transfer saveIfAbsent(Transfer transfer);

    Transfer save(Transfer transfer);
}
