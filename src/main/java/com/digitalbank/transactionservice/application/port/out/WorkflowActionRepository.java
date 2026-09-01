package com.digitalbank.transactionservice.application.port.out;

import java.util.UUID;

public interface WorkflowActionRepository {

    boolean recordIfAbsent(WorkflowAction action);

    long countByTransferId(UUID transferId);
}
