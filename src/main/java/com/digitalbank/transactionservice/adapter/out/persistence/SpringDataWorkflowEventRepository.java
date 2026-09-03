package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkflowEventRepository extends JpaRepository<WorkflowEventJpaEntity, String> {

    List<WorkflowEventJpaEntity> findByTransferIdAndStatusOrderByCreatedAtAsc(UUID transferId, EventStatus status);
}
