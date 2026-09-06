create table transfer_terminal_event_outbox (
    event_id uuid not null,
    event_type varchar(100) not null,
    schema_version varchar(20) not null,
    producer varchar(100) not null,
    occurred_at timestamp with time zone not null,
    aggregate_id uuid not null,
    correlation_id varchar(100) not null,
    causation_id varchar(150) not null,
    transaction_id uuid not null,
    source_account_id uuid not null,
    destination_account_id uuid not null,
    amount numeric(19, 4) not null,
    currency varchar(3) not null,
    transfer_request_id varchar(100) not null,
    reservation_request_id varchar(100),
    reservation_id varchar(100),
    posting_request_id varchar(100),
    posting_id varchar(150),
    status varchar(40) not null,
    failure_stage varchar(40),
    failure_code varchar(80),
    failure_reason varchar(500),
    compensation_status varchar(30),
    manual_review_required boolean,
    event_status varchar(20) not null,
    attempt_count integer not null default 0,
    available_at timestamp with time zone not null,
    published_at timestamp with time zone,
    last_error varchar(2000),
    processing_token uuid,
    processing_until timestamp with time zone,
    json_payload text not null,
    created_at timestamp with time zone not null default now(),
    constraint pk_transfer_terminal_event_outbox primary key (event_id),
    constraint uq_transfer_terminal_event_outbox_aggregate unique (aggregate_id),
    constraint ck_transfer_terminal_event_outbox_type check (
        event_type in ('TransferCompleted.v1', 'TransferFailed.v1')
    ),
    constraint ck_transfer_terminal_event_outbox_schema check (schema_version = '1.0.0'),
    constraint ck_transfer_terminal_event_outbox_amount check (amount > 0),
    constraint ck_transfer_terminal_event_outbox_status check (status in ('COMPLETED', 'FAILED')),
    constraint ck_transfer_terminal_event_outbox_event_status check (event_status in ('PENDING', 'PUBLISHED', 'FAILED')),
    constraint ck_transfer_terminal_event_outbox_attempts check (attempt_count >= 0),
    constraint ck_transfer_terminal_event_outbox_type_status check (
        (event_type = 'TransferCompleted.v1' and status = 'COMPLETED')
        or (event_type = 'TransferFailed.v1' and status = 'FAILED')
    )
);

create index idx_transfer_terminal_event_outbox_ready
    on transfer_terminal_event_outbox (event_status, available_at, created_at);

create index idx_transfer_terminal_event_outbox_posting_request
    on transfer_terminal_event_outbox (posting_request_id);
