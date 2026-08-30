# Transfer Saga Process Manager Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a durable, transport-neutral transfer saga/process-manager foundation for `.github#103`, based on the lifecycle foundation in transaction-service PR #6.

**Architecture:** A framework-free `Transfer` aggregate owns legal workflow transitions and immutable correlation/request metadata. An application service consumes typed command/event messages, persists the workflow, inbox records, and deterministic next actions transactionally through ports, and defers out-of-order ledger events until reservation success. Spring Data JPA/Flyway provide the PostgreSQL adapter; no HTTP, Kafka, topic, or schema-registry adapter is introduced.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, AssertJ, Testcontainers PostgreSQL, Maven, Helm.

**Spec:** `docs/superpowers/specs/2026-08-30-transfer-saga-process-manager-design.md`

## Global Constraints

- Preserve the base package `com.digitalbank.transactionservice` and service name `transaction-service`.
- Keep application and domain code free of Kafka, HTTP, concrete topics, and account/ledger balance mutation.
- Every workflow command/event carries `transferId`, `correlationId`, and a request or event id; related reservation/posting identifiers are validated.
- `PENDING` means awaiting account reservation; `AWAITING_LEDGER_POSTING` means reservation succeeded and ledger posting is next.
- Duplicate semantic messages and duplicate event IDs are no-ops; out-of-order ledger events are durably deferred and replayed after reservation success.
- Workflow updates use optimistic locking; state, inbox status, and newly recorded actions are committed in one transaction.
- Use focused tests before production code for each behavior, then run `./mvnw --batch-mode --no-transfer-progress verify` and the documented Helm checks.

---

### Task 1: Expand The Transfer Aggregate And Message Model

**Files:**
- Modify: `src/main/java/com/digitalbank/transactionservice/domain/TransferStatus.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/domain/Transfer.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/domain/IllegalTransferTransitionException.java`
- Create: `src/main/java/com/digitalbank/transactionservice/domain/TransferConflictException.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/RequestTransferCommand.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/AccountReservationCreated.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/AccountReservationRejected.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/LedgerPostingCompleted.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/in/LedgerPostingFailed.java`
- Test: `src/test/java/com/digitalbank/transactionservice/domain/TransferTest.java`
- Create: `src/test/java/com/digitalbank/transactionservice/application/port/in/WorkflowMessageTest.java`

**Interfaces:**
- `Transfer.request(UUID, UUID, UUID, BigDecimal, String, String, String, String, String)` creates version 0 in `PENDING`.
- `Transfer.rehydrate(UUID, UUID, UUID, BigDecimal, String, String, String, String, String, String, TransferStatus, long)` restores durable state.
- `Transfer.accountReservationCreated(String, String, String)` returns whether it advanced from `PENDING` and stores reservation metadata.
- `Transfer.accountReservationRejected()` moves `PENDING` to `FAILED`.
- `Transfer.ledgerPostingCompleted(String)` and `Transfer.ledgerPostingFailed(String)` operate on `AWAITING_LEDGER_POSTING`.
- Records expose canonical accessors and reject null/blank identifiers and invalid amounts/currencies in compact constructors.

- [ ] **Step 1: Write failing aggregate and message tests.** Cover request creation metadata, legal reservation/posting success/failure transitions, retained reversal behavior, illegal terminal transitions, duplicate same-outcome transitions, correlation/request mismatch validation, and message identifier validation.
- [ ] **Step 2: Run the focused tests and verify the expected red state.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferTest,WorkflowMessageTest test`

Expected: compilation/test failure because the new statuses, constructors, transition methods, and message records do not exist.

- [ ] **Step 3: Implement the smallest domain/message surface.** Add the two workflow states, immutable aggregate fields, version increment only on real transitions, same-outcome idempotency, and explicit `TransferConflictException` for payload/correlation conflicts.
- [ ] **Step 4: Run the focused tests and verify green.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferTest,WorkflowMessageTest test`

Expected: all focused tests pass.

- [ ] **Step 5: Commit the aggregate/message slice.**

```bash
git add src/main/java/com/digitalbank/transactionservice/domain src/main/java/com/digitalbank/transactionservice/application/port/in src/test/java/com/digitalbank/transactionservice/domain src/test/java/com/digitalbank/transactionservice/application/port/in
git commit -m "feat: model transfer saga workflow states"
```

### Task 2: Add Transport-Neutral Process-Manager Ports And Unit Behavior

**Files:**
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/TransferWorkflowRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/WorkflowEventInbox.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/WorkflowActionRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/WorkflowEventRecord.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/WorkflowAction.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/RequestAccountReservation.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/RequestLedgerPosting.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/port/out/ReleaseAccountReservation.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/service/TransferProcessManager.java`
- Create: `src/main/java/com/digitalbank/transactionservice/application/service/WorkflowResult.java`
- Create: `src/test/java/com/digitalbank/transactionservice/application/service/TransferProcessManagerTest.java`

**Interfaces:**
- `TransferWorkflowRepository.findById(UUID)` and `save(Transfer)` provide aggregate persistence.
- `WorkflowEventInbox.findByEventId(String)`, `defer(WorkflowEventRecord)`, `markProcessed(String)`, and `findDeferredByTransferId(UUID)` provide idempotent/deferred event handling.
- `WorkflowActionRepository.recordIfAbsent(WorkflowAction)` records deterministic actions and returns whether this call inserted one.
- `TransferProcessManager.requestTransfer(RequestTransferCommand)` and `handle(AccountReservationCreated|AccountReservationRejected|LedgerPostingCompleted|LedgerPostingFailed)` return `WorkflowResult(Transfer, List<WorkflowAction>)`.
- `WorkflowAction.actionId()` is deterministic: `account-reservation:<reservationRequestId>`, `ledger-posting:<postingRequestId>`, or `release-reservation:<reservationId>`.

- [ ] **Step 1: Write failing process-manager tests with in-memory test doubles.** Cover initial reservation action, reservation success and ledger action, reservation rejection, ledger success, ledger failure and release action, duplicate event IDs, duplicate semantic events with different event IDs, out-of-order ledger event deferral/replay, correlation mismatches, duplicate request commands, conflicting request commands, and action idempotency after retry.
- [ ] **Step 2: Run the focused test and verify it fails for missing process-manager ports/service.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferProcessManagerTest test`

Expected: compilation failure because the process-manager types are not yet implemented.

- [ ] **Step 3: Implement ports, actions, and the process manager.** Validate correlation before any inbox write, treat already processed event IDs as no-ops, defer only ledger events received while `PENDING`, replay deferred events after reservation success, and record actions only after a real transition.
- [ ] **Step 4: Run the focused process-manager tests and verify green.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferProcessManagerTest test`

Expected: all process-manager tests pass with no Spring or transport dependency.

- [ ] **Step 5: Commit the application boundary.**

```bash
git add src/main/java/com/digitalbank/transactionservice/application src/test/java/com/digitalbank/transactionservice/application
git commit -m "feat: add transport-neutral transfer process manager"
```

### Task 3: Add Durable JPA/Flyway Workflow Persistence

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V1__create_transfer_workflow.sql`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/TransferWorkflowJpaEntity.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/WorkflowEventJpaEntity.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/WorkflowActionJpaEntity.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/SpringDataTransferWorkflowRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/SpringDataWorkflowEventRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/SpringDataWorkflowActionRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/PostgresTransferWorkflowRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/PostgresWorkflowEventInbox.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/PostgresWorkflowActionRepository.java`
- Create: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/TransferWorkflowJpaMapper.java`
- Create: `src/test/java/com/digitalbank/transactionservice/TransferWorkflowPersistenceIT.java`
- Modify: `src/test/resources/application.properties`

**Interfaces:**
- `transfer_workflows` has unique `id`, `correlation_id`, request identifiers, workflow state, and `version`; `@Version` maps the version column.
- `transfer_workflow_events` has unique `event_id`, event metadata, `event_status` (`DEFERRED`/`PROCESSED`), and enough typed identifiers to replay a deferred ledger event.
- `transfer_workflow_actions` has unique `action_id`, action type, correlation/request identifiers, and typed payload columns.
- The adapters implement only the application ports; no Kafka or HTTP classes are added.

- [ ] **Step 1: Add the failing persistence test and dependency declarations.** The test starts a PostgreSQL Testcontainer, requests a transfer through the application service, reloads state, verifies action/event rows survive, verifies deferred event replay survives, and verifies a stale aggregate update raises an optimistic-lock exception.
- [ ] **Step 2: Run the persistence test and verify the expected red state.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferWorkflowPersistenceIT test`

Expected: compilation or missing-schema failure before the adapter and migration exist.

- [ ] **Step 3: Implement the schema and JPA adapters.** Match the existing ledger-service dependency/test style, use `spring.jpa.hibernate.ddl-auto=validate`, set `open-in-view=false`, map `@Version`, and flush writes so stale versions fail inside the process-manager transaction.
- [ ] **Step 4: Run the persistence test and verify green.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferWorkflowPersistenceIT test`

Expected: all persistence assertions pass when Docker is available.

- [ ] **Step 5: Commit the durable adapter.**

```bash
git add pom.xml src/main/resources/db/migration src/main/java/com/digitalbank/transactionservice/adapter/out/persistence src/test/java/com/digitalbank/transactionservice/TransferWorkflowPersistenceIT.java src/test/resources/application.properties
git commit -m "feat: persist transfer workflow state and actions"
```

### Task 4: Wire Transactional Application Execution Without Transport Adapters

**Files:**
- Modify: `src/main/java/com/digitalbank/transactionservice/application/service/TransferProcessManager.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application.properties`
- Modify: `src/test/java/com/digitalbank/transactionservice/TransactionServiceApplicationIT.java`
- Create: `src/test/java/com/digitalbank/transactionservice/application/service/TransferProcessManagerSpringIT.java`

**Interfaces:**
- Annotate process-manager entry points with Spring transaction boundaries while keeping constructor-based unit testability.
- Keep application startup self-contained with an H2 test datasource; production remains PostgreSQL/Flyway-configured through Config Server.

- [ ] **Step 1: Write the failing Spring integration test.** Verify the real Spring process manager and JPA adapters can request a transfer, reload it, and return the deterministic reservation action without requiring Config Server.
- [ ] **Step 2: Run the test and verify the expected red state.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferProcessManagerSpringIT test`

Expected: failure until the test datasource, transaction wiring, and adapter beans are complete.

- [ ] **Step 3: Add the test H2 datasource and transaction annotations/configuration.** Do not expose a controller or change the service’s HTTP API.
- [ ] **Step 4: Run the Spring test and the existing health test.**

Run: `./mvnw --batch-mode --no-transfer-progress -Dtest=TransferProcessManagerSpringIT,TransactionServiceApplicationIT test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit the application wiring.**

```bash
git add src/main/java/com/digitalbank/transactionservice/application/service/TransferProcessManager.java src/main/resources/application.properties src/test/resources/application.properties src/test/java/com/digitalbank/transactionservice
git commit -m "test: verify transactional transfer workflow wiring"
```

### Task 5: Update Boundary Documentation And Validate The Delivery Stack

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/superpowers/specs/2026-08-30-transfer-saga-process-manager-design.md`

- [ ] **Step 1: Update documentation.** Replace the bootstrap-only planned-scope wording with the implemented process-manager boundary, state the durable tables and retry/deferred-event rules, and explicitly retain exclusions for HTTP, Kafka adapters/topics, concrete schemas, account balance mutation, ledger entry mutation, and reversal orchestration.
- [ ] **Step 2: Run formatting and focused static checks.**

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 3: Run Maven verification.**

Run: `./mvnw --batch-mode --no-transfer-progress verify`

Expected: unit and integration phases pass; if Docker is unavailable, report the persistence-test environment limitation rather than masking it.

- [ ] **Step 4: Run Helm checks from the repository README.**

```bash
helm lint helm --strict --values helm/values-sit.yaml
helm template transaction-service helm --namespace digital-bank-sit --values helm/values-sit.yaml --set image.tag="verification" > /tmp/transaction-service-rendered.yaml
test -s /tmp/transaction-service-rendered.yaml
```

Expected: lint succeeds and rendered output is non-empty.

- [ ] **Step 5: Inspect the final diff and commit documentation.**

```bash
git status --short
git diff --stat feature/103-transfer-lifecycle-foundation...HEAD
git diff --check feature/103-transfer-lifecycle-foundation...HEAD
git add README.md AGENTS.md docs/superpowers/specs/2026-08-30-transfer-saga-process-manager-design.md
git commit -m "docs: document transfer saga foundation"
```

### Task 6: Push And Open The Non-Draft Stacked PR

**Files:**
- No source files; GitHub PR metadata only.

- [ ] **Step 1: Confirm branch and working tree state.** Ensure only the intentionally untracked `task-4-brief.md` remains untracked and no existing user changes were altered.
- [ ] **Step 2: Push the feature branch without merging.**

```bash
git push --set-upstream origin feature/103-transfer-saga
```

- [ ] **Step 3: Create or update the PR with valid Markdown.** Target `feature/103-transfer-lifecycle-foundation`, link `.github#103`, lifecycle PR #6, and bootstrap PR #5, state that #5 must merge before #6 and #6 before this PR, and state that this PR is intentionally non-draft and must not be merged as part of the task.
- [ ] **Step 4: Verify PR metadata.**

```bash
gh pr view --repo digital-bank-java/transaction-service --json number,title,state,isDraft,url,baseRefName,headRefName,body
```

Expected: an open, non-draft PR URL with the intended base/head and Markdown body.
