# Account Reservation Transport Design

## Goal

Add the Transaction Service side of the governed Sprint 3 account-reservation
transport while preserving the existing internal workflow HTTP contract and
`TransferCreated.v1` behavior.

## Boundaries

Transaction Service owns transfer workflow state and emits reservation command
events. Account Service owns reservation state and emits reservation facts.
This change adds no AWS, UAT, PROD, infrastructure provisioning, schema
registry, public HTTP, ledger mutation, or second saga behavior.

## Design

`TransferProcessManager` remains the orchestration boundary and emits the
account-reservation request command through a durable outbox in the same Spring
transaction. Account Service owns reservation release after a ledger failure;
Transaction Service records the intermediate
`AWAITING_RESERVATION_RELEASE` state and waits for the resulting
`AccountReservationReleased` fact instead of emitting a second release command.
The outbox stores the exact serialized payload, event type, aggregate and
source-account key, attempt state, and processing lease. A scheduled relay
claims rows atomically, publishes the stored payload with the six governed
headers, and marks success or retryable failure using the same
lease/idempotency pattern as the existing `TransferCreated` relay.

Reservation commands use deterministic event IDs derived from their
deterministic workflow action IDs. This makes duplicate workflow requests and
relay retries reuse the same event ID and payload. The outbox is always
persisted; Kafka publication is disabled by default.

Inbound Kafka adapters deserialize the four reservation facts, validate the
required envelope and kebab-case headers, require header values to equal their
payload counterparts, and enforce the event-specific producer/type/version
identifiers. They then map to application input records and call
`TransferProcessManager`. Listeners contain no orchestration decisions;
transactional inbox deduplication remains in the process manager path.

The transfer aggregate adds `AWAITING_RESERVATION_RELEASE` as its sole new
workflow status. A reservation rejection fails a pending transfer without a
release. A ledger failure moves an accepted transfer to
`AWAITING_RESERVATION_RELEASE` and waits for Account Service's release fact.
The released fact then moves that workflow to terminal `FAILED`. An expiry fact
moves the workflow directly to terminal `FAILED` and never records a second
release. Duplicate and stale facts are idempotent or rejected through existing
identity checks and the durable inbox.

Kafka transport properties are explicit and default to disabled. A plaintext
broker is permitted only when the SIT profile and an explicit SIT plaintext
flag are both present; non-SIT plaintext configuration fails fast. No runtime
secret or environment-specific broker configuration is committed.

## Verification

Focused tests cover event serialization and header validation, reservation
outbox atomicity/replay and relay leases, listener redelivery deduplication,
and the new process-manager state mappings. Existing process-manager, HTTP,
and `TransferCreated` tests remain authoritative for unchanged behavior.
