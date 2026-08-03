# Transaction Service

Transaction Service is the planned transfer-orchestration and saga/process-manager service for the Digital Bank Java platform.

## Current State

This repository currently contains the reviewed bootstrap plan only. It does not yet contain a Spring Boot application, Docker image, Helm chart, API, database schema, Kafka consumer/producer, or deployable SIT workload. Do not treat this repository as an operational service yet.

## Intended Responsibilities

- Orchestrate transfer workflows and their explicit lifecycle states.
- Coordinate account reservation, ledger posting, completion, and compensation through events.
- Maintain transfer-level idempotency and recoverable saga state.
- Consume and publish versioned event contracts as the event-driven platform is implemented.

## Non-Responsibilities

- Directly own customer profiles, account balances, or immutable ledger records.
- Expose a public API that changes balances without the account-reservation and ledger-posting workflow.
- Deploy or operate shared Kafka or PostgreSQL infrastructure.

## Planned Bootstrap

The approved implementation sequence is documented in [the bootstrap plan](docs/superpowers/plans/2026-08-02-transaction-service-bootstrap.md). It covers the Spring Boot service boundary, Config Server client integration, Maven test phases, Docker packaging, Helm deployment, CI, and repository guidance.

SIT is the lowest formal runtime environment. A workstation JVM, once the application exists, will be a temporary debugging process connected to forwarded SIT dependencies rather than a separate `local` profile or deployment environment.

## Contribution Workflow

Do not add implementation work without a tracked GitHub issue and pull request. Follow the organization [README standard](https://github.com/digital-bank-java/.github/blob/main/docs/readme-standard.md), [platform conventions](https://github.com/digital-bank-java/.github/blob/main/docs/platform-conventions.md), and the repository bootstrap plan. Never commit credentials, tokens, or production endpoints.
