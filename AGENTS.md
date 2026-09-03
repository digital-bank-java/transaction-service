# Transaction Service Agent Guide

## Repository Purpose

This repository contains the deployable bootstrap for the future Transaction
Service. The service will eventually own transfer orchestration and the
transfer saga/process-manager foundation.

## Current Boundary

The current implementation owns only:

- Spring Boot application startup.
- Config Server client configuration.
- Actuator health and Kubernetes probe endpoints.
- Maven, Docker, and Helm delivery foundations.
- A framework-free transfer lifecycle model with deterministic state transitions.
- An internal-only HTTP endpoint for transfer workflow requests and idempotent
  replays.
- Transfer saga/process-manager application boundary and workflow state.
- PostgreSQL persistence for transfer workflows, inbox records, and actions.
- Governed Kafka transport for account-reservation and ledger-posting workflow
  messages, backed by transactional outbox and durable inbox persistence.

It must not implement public transfer endpoints, direct account balance or
ledger-entry mutation, public gateway routing, or ownership of reservations or
ledger postings. Account Service owns account reservations and projections;
Ledger Service owns immutable postings.

## Architecture And Naming

- Base package: `com.digitalbank.transactionservice`.
- Service and Config Server name: `transaction-service`.
- Default HTTP port: `8084`, supplied by Config Server at runtime.
- SIT namespace: `digital-bank-sit`.
- In-cluster Config Server: `http://config-server:8888`.
- Image: `digital-bank-java/transaction-service:<tag>`.

Follow the platform's hexagonal architecture. Domain and application rules
must stay independent of controllers, Helm templates, Kafka, and database
entities. The process manager coordinates through input messages and output
action/repository ports. The internal HTTP adapter at
`/internal/v1/transfer-workflows` is for workflow orchestration only and is not
a customer-facing balance mutation API. The outbound
`ledger.posting.requested.v1` contract must remain directly mappable to Ledger
Service `PostLedgerEntryCommand`, including `description`, `effectiveAt`,
`debitLines`, and `creditLines`.

## Local Commands

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
docker build -t digital-bank-java/transaction-service:0.0.1 .
helm lint helm --strict
```

Normal startup requires Config Server. Automated tests disable Config Client
and must not depend on a running external service.

## Testing Rules

- Write a focused test before adding behavior.
- Surefire runs `*Test.java` and `*Tests.java`.
- Failsafe runs `*IT.java`, `*IntegrationTest.java`, and
  `*IntegrationTests.java` during `verify`.
- The bootstrap health contract is verified at a random HTTP port and must
  report `UP` from `/actuator/health`.
- Process-manager tests must cover legal/illegal transitions, duplicate and
  out-of-order events, correlation mismatches, and retry-safe actions.
- PostgreSQL persistence tests use Testcontainers and verify optimistic locking,
  inbox durability, and action durability.
- Use `./mvnw --batch-mode --no-transfer-progress verify` as the local quality
  gate.

## Container And Helm Rules

The image must use Java 21, run as `10001:10001`, expose `8084`, and remain
usable with a read-only root filesystem. Helm must set
`CONFIG_SERVER_URL` and `SPRING_PROFILES_ACTIVE`, mount writable `/tmp`,
disable privilege escalation, drop all capabilities, and provide startup,
liveness, and readiness probes against the Actuator health groups.

Use the chart helpers for names and selectors. The Service selector must
match the Deployment labels.

## Security And Delivery

- Never commit credentials, tokens, passwords, or real environment secrets.
- Keep runtime configuration in Config Server/config-repo and use placeholders
  or environment variables for sensitive values.
- Work on a dedicated branch and do not commit directly to `main`.
- Update README and this file when the supported boundary or commands change.
