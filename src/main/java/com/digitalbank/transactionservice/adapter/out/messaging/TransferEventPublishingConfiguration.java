package com.digitalbank.transactionservice.adapter.out.messaging;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "transaction.events.transfer-created.enabled", havingValue = "true")
class TransferEventPublishingConfiguration {}
