# Transfer Saga Process-Manager Design

## Objective

Implement the Sprint 3 foundation for `.github#103`, extending the existing
framework-free `Transfer` lifecycle into a durable, transport-neutral
process-manager boundary. The boundary coordinates account reservation and
ledger posting without owning account balances or ledger entries.

## Scope

The process manager supports these application messages:

1. `RequestTransfer`: creates a transfer in `PENDING` and records a request for
   account reservation.
2. `AccountReservationCreated`: advances a pending transfer to
   `AWAITING_LEDGER_POSTING` and records a ledger-posting request.
3. `AccountReservationRejected`: fails a pending transfer without issuing a
   release for a reservation that does not exist.
4. `LedgerPostingCompleted`: advances a transfer awaiting posting to
   `COMPLETED`.
5. `LedgerPostingFailed`: fails a transfer awaiting posting and records an
   explicit account-reservation release action.

The existing `COMPLETED -> REVERSED` lifecycle remains domain behavior, but
reversal orchestration is outside this slice. No HTTP controller, Kafka
dependency, topic, schema registry configuration, or concrete message adapter
is added.

## State Model

The transfer aggregate stores its immutable transfer details, workflow
correlation, request identifiers, related reservation/posting identifiers, and
an optimistic-lock version.

| Current state | Message | Next state | Action |
| --- | --- | --- | --- |
| new | `RequestTransfer` | `PENDING` | request account reservation |
| `PENDING` | `AccountReservationCreated` | `AWAITING_LEDGER_POSTING` | request ledger posting |
| `PENDING` | `AccountReservationRejected` | `FAILED` | none |
| `AWAITING_LEDGER_POSTING` | `LedgerPostingCompleted` | `COMPLETED` | none |
| `AWAITING_LEDGER_POSTING` | `LedgerPostingFailed` | `FAILED` | release account reservation |
| `COMPLETED` | reversal command | `REVERSED` | outside this slice |

Repeated handling of the same semantic event is a no-op. An event with a
different transfer correlation, reservation identifier, or posting request
identifier is rejected and does not mutate state. An event received before
its predecessor is ignored as out-of-order when it cannot legally advance the
workflow; it does not create an action or change the stored version.

## Identifiers And Idempotency

Every request and message includes:

- `transferId`: stable workflow identity;
- `correlationId`: stable transfer-level correlation;
- `requestId` or event `eventId`: stable delivery/idempotency identity;
- `reservationRequestId`, `reservationId`, or `postingRequestId` as applicable.

The first request's identifiers and immutable transfer payload become part of
the workflow record. A repeated `RequestTransfer` with the same transfer and
payload returns the existing workflow and does not add another action. A
different payload or correlation for the same transfer is a conflict.

Consumed event IDs are stored in an inbox table with a unique constraint. A
deterministic action ID and unique constraint make action recording retry-safe,
including retries after a process-manager transaction is interrupted.

## Persistence And Concurrency

The application layer depends on ports for workflow state, consumed events,
and next-action recording. A PostgreSQL adapter uses Spring Data JPA and
Flyway. The workflow table stores `version` for optimistic locking; an update
that observes a stale version fails rather than overwriting a concurrent
transition. Workflow state, event claiming, and action recording are joined
by one application transaction.

The persistence schema is limited to transaction-service-owned workflow data:

- `transfer_workflows`: durable aggregate state and correlation metadata;
- `transfer_workflow_events`: inbox/deduplication records keyed by event ID;
- `transfer_workflow_actions`: durable next actions keyed by deterministic
  action ID.

This service never updates account balances or ledger entries. Its account and
ledger actions are records for future adapters to deliver to governed
contracts.

## Testing Strategy

Unit tests cover legal and illegal aggregate transitions, duplicate events,
out-of-order events, correlation/request mismatches, duplicate transfer
requests, and retry-safe action creation. A persistence integration test uses
the existing PostgreSQL/Testcontainers convention to verify state/action/event
durability and optimistic version behavior. The existing health integration
test remains unchanged.

## Documentation And Delivery

`README.md` and `AGENTS.md` will state that the process-manager boundary and
durable workflow foundation are implemented, while HTTP, Kafka, outbox
publication, concrete event schemas, account reservation execution, ledger
posting, and production reconciliation remain outside this PR.

The implementation branch is `feature/103-transfer-saga`, based on
`feature/103-transfer-lifecycle-foundation`. The PR targets lifecycle PR #6,
which is itself stacked on bootstrap PR #5; no PR is merged by this work.
