package com.digitalbank.transactionservice.application.service;

import com.digitalbank.transactionservice.application.port.out.WorkflowAction;
import com.digitalbank.transactionservice.domain.Transfer;
import java.util.List;

public record WorkflowResult(Transfer transfer, List<WorkflowAction> actions, boolean created) {

    public WorkflowResult(Transfer transfer, List<WorkflowAction> actions) {
        this(transfer, actions, !actions.isEmpty());
    }

    public WorkflowResult {
        actions = List.copyOf(actions);
    }
}
