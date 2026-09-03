create table reservation_command_event_outbox (
    event_id uuid not null,
    event_type varchar(100) not null,
    schema_version varchar(20) not null,
    producer varchar(100) not null,
    occurred_at timestamp with time zone not null,
    aggregate_id varchar(150) not null,
    correlation_id varchar(100) not null,
    causation_id varchar(150) not null,
    transaction_id uuid not null,
    reservation_request_id varchar(100) not null,
    source_account_id uuid not null,
    destination_account_id uuid,
    amount numeric(19, 4),
    currency varchar(3),
    reservation_id varchar(100),
    posting_request_id varchar(100),
    expires_at timestamp with time zone,
    reason varchar(100),
    event_status varchar(20) not null,
    attempt_count integer not null default 0,
    available_at timestamp with time zone not null,
    published_at timestamp with time zone,
    last_error varchar(2000),
    processing_token uuid,
    processing_until timestamp with time zone,
    json_payload text not null,
    created_at timestamp with time zone not null default now(),
    constraint pk_reservation_command_event_outbox primary key (event_id),
    constraint uq_reservation_command_event_outbox_aggregate_type unique (aggregate_id, event_type),
    constraint ck_reservation_command_event_outbox_type check (
        event_type in ('AccountReservationRequested.v1', 'AccountReservationReleaseRequested.v1')),
    constraint ck_reservation_command_event_outbox_schema check (schema_version = '1.0.0'),
    constraint ck_reservation_command_event_outbox_amount check (amount is null or amount > 0),
    constraint ck_reservation_command_event_outbox_status check (event_status in ('PENDING', 'PUBLISHED', 'FAILED')),
    constraint ck_reservation_command_event_outbox_attempts check (attempt_count >= 0)
);

create index idx_reservation_command_event_outbox_ready
    on reservation_command_event_outbox (event_status, available_at, created_at);

create index idx_reservation_command_event_outbox_processing
    on reservation_command_event_outbox (processing_until, event_status, available_at, created_at);
