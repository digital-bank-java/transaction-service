package com.digitalbank.transactionservice.risk;

import java.time.Instant;

public interface TransferRiskEvaluator {

    TransferRiskDecision evaluate(TransferRiskIntent intent, Instant now);
}
