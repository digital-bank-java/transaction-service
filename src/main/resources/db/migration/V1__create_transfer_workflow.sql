create table transfer_workflows (
    id uuid not null,
    source_account_id uuid not null,
    destination_account_id uuid not null,
    amount numeric(19, 4) not null,
    currency varchar(3) not null,
    correlation_id varchar(100) not null,
    transfer_request_id varchar(100) not null,
    reservation_request_id varchar(100) not null,
    posting_request_id varchar(100) not null,
    reservation_id varchar(100),
    status varchar(40) not null,
    version bigint not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint pk_transfer_workflows primary key (id),
    constraint uq_transfer_workflows_correlation_id unique (correlation_id),
    constraint uq_transfer_workflows_transfer_request_id unique (transfer_request_id),
    constraint uq_transfer_workflows_reservation_request_id unique (reservation_request_id),
    constraint uq_transfer_workflows_posting_request_id unique (posting_request_id),
    constraint ck_transfer_workflows_amount_positive check (amount > 0),
    constraint ck_transfer_workflows_currency_format check (currency = upper(currency)),
    constraint ck_transfer_workflows_status check (
        status in ('PENDING', 'AWAITING_LEDGER_POSTING', 'COMPLETED', 'FAILED', 'REVERSED'))
);

create table transfer_workflow_events (
    event_id varchar(150) not null,
    transfer_id uuid not null,
    event_type varchar(60) not null,
    correlation_id varchar(100) not null,
    request_id varchar(100) not null,
    reservation_request_id varchar(100),
    reservation_id varchar(100),
    posting_request_id varchar(100),
    reason varchar(500),
    event_status varchar(20) not null,
    created_at timestamp with time zone not null default now(),
    constraint pk_transfer_workflow_events primary key (event_id),
    constraint ck_transfer_workflow_events_status check (event_status in ('DEFERRED', 'PROCESSED'))
);

create index idx_transfer_workflow_events_deferred
    on transfer_workflow_events (transfer_id, event_status, created_at);

create table transfer_workflow_actions (
    action_id varchar(200) not null,
    transfer_id uuid not null,
    action_type varchar(60) not null,
    correlation_id varchar(100) not null,
    source_account_id uuid,
    destination_account_id uuid,
    amount numeric(19, 4),
    currency varchar(3),
    request_id varchar(100),
    reservation_request_id varchar(100),
    posting_request_id varchar(100),
    reservation_id varchar(100),
    created_at timestamp with time zone not null default now(),
    constraint pk_transfer_workflow_actions primary key (action_id)
);

create index idx_transfer_workflow_actions_transfer_id
    on transfer_workflow_actions (transfer_id);
