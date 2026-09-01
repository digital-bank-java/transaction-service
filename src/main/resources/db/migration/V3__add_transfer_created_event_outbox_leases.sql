alter table transfer_created_event_outbox
    add column processing_token uuid;

alter table transfer_created_event_outbox
    add column processing_until timestamp with time zone;

create index idx_transfer_created_event_outbox_processing
    on transfer_created_event_outbox (processing_until, event_status, available_at, created_at);
