package com.digitalbank.transactionservice.application.port.out;

public interface WorkflowActionRepository {

    boolean recordIfAbsent(WorkflowAction action);
}
