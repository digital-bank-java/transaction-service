package com.digitalbank.transactionservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkflowActionRepository extends JpaRepository<WorkflowActionJpaEntity, String> {

    long countByTransferId(UUID transferId);
}
