package com.digitalbank.transactionservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTransferWorkflowRepository extends JpaRepository<TransferWorkflowJpaEntity, UUID> {}
