package com.digitalbank.transactionservice.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowEventInbox {

    Optional<WorkflowEventRecord> findByEventId(String eventId);

    void defer(WorkflowEventRecord event);

    void recordProcessed(WorkflowEventRecord event);

    void markProcessed(String eventId);

    List<WorkflowEventRecord> findDeferredByTransferId(UUID transferId);
}
