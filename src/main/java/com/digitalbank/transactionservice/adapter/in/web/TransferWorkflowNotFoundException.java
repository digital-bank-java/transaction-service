package com.digitalbank.transactionservice.adapter.in.web;

import java.util.UUID;

class TransferWorkflowNotFoundException extends RuntimeException {

    TransferWorkflowNotFoundException(UUID transferId) {
        super("Transfer workflow not found: " + transferId);
    }
}
