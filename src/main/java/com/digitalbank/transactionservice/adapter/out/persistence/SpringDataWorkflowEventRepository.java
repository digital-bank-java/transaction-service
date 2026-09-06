package com.digitalbank.transactionservice.adapter.out.persistence;

import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventStatus;
import com.digitalbank.transactionservice.application.port.out.WorkflowEventRecord.EventType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkflowEventRepository extends JpaRepository<WorkflowEventJpaEntity, String> {

    List<WorkflowEventJpaEntity> findByTransferIdAndStatusOrderByCreatedAtAsc(UUID transferId, EventStatus status);

    Optional<WorkflowEventJpaEntity> findFirstByTransferIdAndEventTypeAndStatusOrderByCreatedAtDesc(
            UUID transferId, EventType eventType, EventStatus status);
}
