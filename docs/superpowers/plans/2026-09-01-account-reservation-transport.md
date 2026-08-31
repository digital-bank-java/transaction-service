# Account Reservation Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement Transaction Service reservation commands, facts, durable transport, and governed workflow transitions for Sprint 3.

**Architecture:** Keep `TransferProcessManager` as the only orchestration boundary. Persist reservation command events transactionally with existing workflow actions, relay them through a leased outbox, and map validated inbound Kafka records back into process-manager input ports.

**Tech Stack:** Java 21, Spring Boot 4, Spring Kafka, Spring Data JPA, Flyway, Jackson, JUnit 5, AssertJ, H2, and existing Testcontainers PostgreSQL integration tests.

**Spec:** `docs/superpowers/specs/2026-09-01-account-reservation-transport-design.md`

## Global Constraints

- Keep Kafka transport disabled by default.
- Permit plaintext only with an explicit SIT-only opt-in; fail closed outside SIT.
- Do not add AWS/UAT/PROD deployment or infrastructure configuration.
- Do not publish directly from the HTTP thread outside the database transaction.
- Preserve the current internal HTTP contract and existing `TransferCreated.v1` behavior.
- Keep orchestration in `TransferProcessManager`, not Kafka listeners.
- Use durable inbox/outbox idempotency and existing optimistic workflow persistence.

### Task 1: Add Failing Workflow and Event Contract Tests

**Files:**
- Modify: `src/test/java/com/digitalbank/transactionservice/domain/TransferTest.java`
- Modify: `src/test/java/com/digitalbank/transactionservice/application/service/TransferProcessManagerTest.java`
- Modify: `src/test/java/com/digitalbank/transactionservice/application/port/in/WorkflowMessageTest.java`
- Create: `src/test/java/com/digitalbank/transactionservice/adapter/in/messaging/ReservationEventValidatorTest.java`

**Interfaces:**
- Tests will require accepted, released, and expired application messages.
- Tests will require a validator that rejects missing/mismatched governed headers and payload identity.

- [ ] **Step 1: Write failing tests** for `AWAITING_RESERVATION_RELEASE`, release-fact completion, expiry terminal behavior, and header/payload validation.
- [ ] **Step 2: Run `./mvnw -q -DskipUnitTests=false -Dtest=TransferTest,TransferProcessManagerTest,WorkflowMessageTest,ReservationEventValidatorTest test` and verify the new tests fail for missing types/status/validator.
- [ ] **Step 3: Implement only the message records, status/domain transitions, and validator required by the red tests.
- [ ] **Step 4: Re-run the focused command and verify it passes.

### Task 2: Add Reservation Command Outbox Contracts and Persistence

**Files:**
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/AccountReservationRequestedEvent.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/AccountReservationReleaseRequestedEvent.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/ReservationCommandEvent.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/ReservationCommandEventOutbox.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/ReservationCommandEventOutboxJpaEntity.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/ReservationCommandOutboxStatus.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/SpringDataReservationCommandEventOutboxRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/PostgresReservationCommandEventOutbox.java`
- Create: `src/main/resources/db/migration/V4__create_reservation_command_event_outbox.sql`
- Create: `src/test/java/com/digitalbank/transactionservice/adapter/out/persistence/ReservationCommandEventOutboxTest.java`

**Interfaces:**
- `ReservationCommandEventOutbox.recordIfAbsent(ReservationCommandEvent event)` records the exact JSON transactionally.
- `claimReady`, `markPublished`, and `markFailed` mirror `TransferCreatedEventOutbox` lease semantics.

- [ ] **Step 1: Write failing H2/persistence tests** for one-row idempotency, exact JSON replay, claim lease ownership, and retry state.
- [ ] **Step 2: Run the focused outbox test and verify it fails before the adapter exists.
- [ ] **Step 3: Add the migration, JPA entity, repository queries, and adapter using H2-compatible and PostgreSQL-safe claim/update paths.
- [ ] **Step 4: Re-run the focused outbox test and verify it passes.

### Task 3: Wire Actions to the Transactional Reservation Outbox

**Files:**
- Modify: `src/main/java/com/digitalbank/transactionservice/application/service/TransferProcessManager.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/application/port/out/ReleaseAccountReservation.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/application/port/out/WorkflowAction.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/WorkflowActionJpaEntity.java`
- Modify: `src/test/java/com/digitalbank/transactionservice/application/service/TransferProcessManagerTest.java`
- Modify: `src/test/java/com/digitalbank/transactionservice/TransferWorkflowPersistenceIT.java`

**Interfaces:**
- The process manager receives `ReservationCommandEventOutbox` and records a request/release event after the corresponding deterministic action is recorded.
- Release events include the source account, reservation, posting request when available, and a stable compensation reason.

- [ ] **Step 1: Add failing unit/integration assertions** that request and ledger-failure actions create exactly one matching reservation outbox row and that a persistence failure rolls back the action/workflow transaction.
- [ ] **Step 2:** Run the focused tests and verify the assertions fail.
- [ ] **Step 3: Add the transactional wiring without changing HTTP response mapping or `TransferCreated` recording.
- [ ] **Step 4: Re-run focused unit tests and the PostgreSQL persistence test when Docker is available.

### Task 4: Add Kafka Reservation Command Relay and Security Boundary

**Files:**
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/messaging/KafkaReservationCommandPublisher.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/out/messaging/TransferEventPublishingConfiguration.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application.properties`
- Create: `src/test/java/com/digitalbank/transactionservice/adapter/out/messaging/KafkaReservationCommandPublisherTest.java`

**Interfaces:**
- The scheduled publisher uses `ReservationCommandEventOutbox`, `KafkaTemplate<String,String>`, and the persisted event JSON.
- Command topics are `account.reservation.requested.v1` and `account.reservation.release-requested.v1`.

- [ ] **Step 1: Write failing publisher tests** for topic/key, six headers, success marking, failure retry marking, and exact persisted payload replay.
- [ ] **Step 2:** Run the focused publisher test and verify it fails.
- [ ] **Step 3: Implement conditional scheduling, topic selection, and explicit SIT plaintext validation while leaving all transport beans absent by default.
- [ ] **Step 4: Re-run focused publisher tests and assert default properties do not enable Kafka transport.

### Task 5: Add Validated Inbound Reservation Listeners

**Files:**
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/in/messaging/ReservationEventPayloads.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/in/messaging/ReservationKafkaEventListener.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/in/messaging/ReservationEventHeaderValidator.java`
- Create: `src/test/java/com/digitalbank/transactionservice/adapter/in/messaging/ReservationKafkaEventListenerTest.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/AccountReservationAccepted.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/AccountReservationReleased.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/AccountReservationExpired.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/application/port/out/WorkflowEventRecord.java`

**Interfaces:**
- Listener methods accept `ConsumerRecord<String,String>`, validate governed headers and payload, map to application messages, and call `TransferProcessManager.handle(...)`.
- The process manager and inbox perform durable event-id deduplication; exact redelivery is a successful no-op.

- [ ] **Step 1: Write failing listener tests** for all four event types, missing/mismatched headers, malformed UUID/time fields, duplicate event IDs, and mapping into the process manager.
- [ ] **Step 2:** Run the focused listener test and verify it fails.
- [ ] **Step 3: Implement payload records, listener mapping, and conditional `@KafkaListener` registration with explicit consumer properties.
- [ ] **Step 4: Re-run focused listener tests and verify duplicate delivery invokes no second state transition/action.

### Task 6: Verify, Review, and Commit

**Files:**
- Review all changed transaction-service files; do not change unrelated files.

- [ ] **Step 1: Run `./mvnw --batch-mode --no-transfer-progress verify`.
- [ ] **Step 2: Run `git diff --check`, inspect `git diff --stat`, and verify the public/internal HTTP and existing `TransferCreated` paths remain unchanged.
- [ ] **Step 3: Run a focused code review against the final diff and fix Critical/Important findings.
- [ ] **Step 4: Commit with `feat: add governed reservation kafka transport`.
