# Transfer Risk Decision Gate Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic, configuration-driven transfer-risk decision gate that prevents reservation and ledger actions until a transfer-bound step-up decision is satisfied.

**Architecture:** Transaction Service evaluates a normalized transfer intent through a local `TransferRiskEvaluator` port. The resulting immutable decision is persisted with the transfer workflow, making retries idempotent and ensuring `REQUIRE_STEP_UP`, `DECLINE`, expired, and mismatched decisions fail closed. MFA verification remains outside this change; the existing reservation and ledger saga starts only for `ALLOW`.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL/H2 test fixtures, Spring MVC, Bean Validation, Springdoc OpenAPI.

**Spec:** Organization contract [`docs/contracts/transfer-risk-step-up.md`](https://github.com/digital-bank-java/.github/blob/main/docs/contracts/transfer-risk-step-up.md)

## Global Constraints

- Preserve the existing internal-only transfer workflow route and ledger-driven saga boundary.
- Do not accept or persist OTP values, access tokens, passwords, or raw payment credentials.
- Normalize currency to uppercase, trim identifiers, and compare amounts numerically before idempotency checks.
- Use stable decision outcomes `ALLOW`, `REQUIRE_STEP_UP`, and `DECLINE`.
- Do not hard-code customer-facing risk thresholds in clients; all initial policy values come from configuration.
- Add only focused tests for the new risk gate, its persistence binding, and the no-action fail-closed invariant.
- AWS/UAT/PROD policy storage and deployment remain deferred.

### Task 1: Add the normalized risk contract and configured evaluator

**Files:**
- Create: `src/main/java/com/digitalbank/transactionservice/risk/TransferDestinationClass.java`
- Create: `src/main/java/com/digitalbank/transactionservice/risk/TransferRiskOutcome.java`
- Create: `src/main/java/com/digitalbank/transactionservice/risk/TransferRiskDecision.java`
- Create: `src/main/java/com/digitalbank/transactionservice/risk/TransferRiskIntent.java`
- Create: `src/main/java/com/digitalbank/transactionservice/risk/TransferRiskEvaluator.java`
- Create: `src/main/java/com/digitalbank/transactionservice/risk/ConfiguredTransferRiskEvaluator.java`
- Create: `src/main/java/com/digitalbank/transactionservice/risk/TransferRiskProperties.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/TransactionServiceApplication.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/digitalbank/transactionservice/risk/ConfiguredTransferRiskEvaluatorTest.java`

**Interfaces:**
- Consumes: normalized intent fields `transferId`, `decisionRequestId`, `sourceAccountId`, `destinationAccountId`, `amount`, `currency`, `destinationClass`, `channel`, and `correlationId`.
- Produces: `TransferRiskDecision evaluate(TransferRiskIntent intent, Instant now)` with immutable identifiers, policy version, reason codes, required assurance, and expiry.

- [ ] **Step 1: Write one failing evaluator test** covering configured `INTERNATIONAL` step-up and high-value `USD` step-up, plus a blocked destination decline.
- [ ] **Step 2: Run the focused test and verify it fails because the evaluator contract is absent.**
- [ ] **Step 3: Implement the enums, immutable records, `@ConfigurationProperties(prefix = "transaction.risk")`, and evaluator.** The evaluator must use deterministic UUID generation from policy version, request ID, and normalized intent; map reasons to stable codes; and reject invalid configuration at startup.
- [ ] **Step 4: Run the focused test and then the existing risk-free test suite.**
- [ ] **Step 5: Commit:** `feat: add configured transfer risk evaluator`.

### Task 2: Bind risk decisions to transfer workflow persistence

**Files:**
- Modify: `src/main/java/com/digitalbank/transactionservice/application/port/in/RequestTransferCommand.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/in/web/InternalTransferWorkflowRequest.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/domain/Transfer.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/TransferWorkflowJpaEntity.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/TransferWorkflowJpaMapper.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/out/persistence/SpringDataTransferWorkflowRepository.java`
- Create: `src/main/resources/db/migration/V7__add_transfer_risk_decision_binding.sql`
- Test: `src/test/java/com/digitalbank/transactionservice/domain/TransferRiskBindingTest.java`
- Test: `src/test/java/com/digitalbank/transactionservice/TransferWorkflowPersistenceIT.java`

**Interfaces:**
- Consumes: `TransferRiskDecision` from Task 1.
- Produces: rehydratable `Transfer` risk fields and immutable comparison helpers used by the process manager.

- [ ] **Step 1: Write a failing persistence/domain test** proving a transfer retains outcome, decision ID, request ID, policy version, reasons, and expiry after rehydration.
- [ ] **Step 2: Run the focused test and verify it fails because risk fields are not present.**
- [ ] **Step 3: Add request fields `decisionRequestId` and `destinationClass` with backward-compatible defaults, add domain risk binding, and add nullable-safe JPA/Flyway columns with a unique decision request identifier.**
- [ ] **Step 4: Run the focused persistence test and verify the migration validates under the existing H2/PostgreSQL fixtures.**
- [ ] **Step 5: Commit:** `feat: persist transfer risk decision binding`.

### Task 3: Gate orchestration actions and expose the internal contract

**Files:**
- Modify: `src/main/java/com/digitalbank/transactionservice/application/service/TransferProcessManager.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/in/web/TransferWorkflowResponse.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/in/web/TransferWorkflowController.java`
- Modify: `src/main/java/com/digitalbank/transactionservice/adapter/in/web/ApiExceptionHandler.java`
- Test: `src/test/java/com/digitalbank/transactionservice/application/service/TransferProcessManagerTest.java`
- Test: `src/test/java/com/digitalbank/transactionservice/adapter/in/web/TransferWorkflowApiIT.java`

**Interfaces:**
- Consumes: `TransferRiskEvaluator`, risk-bound `RequestTransferCommand`, and persisted `Transfer` risk state.
- Produces: `201` for a new accepted workflow, `200` with `Idempotent-Replay: true` for an equivalent request, `409` for changed decision intent, and response fields describing the decision without secrets.

- [ ] **Step 1: Write focused failing tests** for `ALLOW` creating exactly one reservation action, `REQUIRE_STEP_UP` creating no reservation/ledger action, `DECLINE` creating no action and ending `FAILED`, and equivalent retries returning the same decision.
- [ ] **Step 2: Run those tests and verify the missing gate causes the expected failures.**
- [ ] **Step 3: Inject the evaluator into `TransferProcessManager`; evaluate before creating outbox actions; persist the decision atomically with the workflow; and preserve existing replay/conflict behavior.**
- [ ] **Step 4: Add explicit OpenAPI response/request schemas and Problem Details for risk conflict and fail-closed outcomes.**
- [ ] **Step 5: Run focused tests and the existing transfer integration tests.**
- [ ] **Step 6: Commit:** `feat: gate transfer workflow on risk decision`.

### Task 4: SIT configuration, documentation, and review delivery

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `helm/values.yaml`
- Modify: `helm/values-sit.yaml`
- Create: `docs/transfer-risk-sit-verification.md`

**Interfaces:**
- Consumes: the `transaction.risk.*` properties from Task 1.
- Produces: repeatable local SIT configuration and verification commands without secret material.

- [ ] **Step 1: Add safe defaults that require explicit policy configuration for production-like profiles and deterministic SIT values for local verification.**
- [ ] **Step 2: Document ALLOW, REQUIRE_STEP_UP, DECLINE, replay, expiry, and the fact that MFA continuation is a follow-up task.**
- [ ] **Step 3: Run `git diff --check`, `./mvnw --batch-mode --no-transfer-progress verify`, and strict Helm lint/template validation.**
- [ ] **Step 4: Build the container only after verification and confirm no secret-like values were introduced.**
- [ ] **Step 5: Commit:** `docs: document transfer risk decision gate`.
- [ ] **Step 6: Push the branch and open a non-draft PR linking `.github#205`, parent Story `.github#55`, and follow-up `.github#56`.**

## Review Checklist

- [ ] Existing low-risk transfer requests retain their current API compatibility and reservation behavior.
- [ ] Risk-required transfers never emit account reservation or ledger posting actions.
- [ ] Declined, expired, malformed, and changed-binding requests fail closed.
- [ ] Database uniqueness and process-manager idempotency protect concurrent retries.
- [ ] No OTP, token, password, or payment credential is accepted or persisted.
- [ ] PR body includes related issue links, merge order, and no fixed delay requirement.
