alter table transfer_workflows drop constraint ck_transfer_workflows_status;

alter table transfer_workflows
    add constraint ck_transfer_workflows_status check (
        status in ('AWAITING_STEP_UP', 'PENDING', 'AWAITING_LEDGER_POSTING',
                   'AWAITING_RESERVATION_RELEASE', 'COMPLETED', 'FAILED', 'REVERSED'));

alter table transfer_workflow_events add column decision_id uuid;
alter table transfer_workflow_events add column subject_id varchar(100);
alter table transfer_workflow_events add column challenge_id varchar(150);
alter table transfer_workflow_events add column assurance_type varchar(50);
alter table transfer_workflow_events add column challenge_type varchar(50);
alter table transfer_workflow_events add column source_account_id uuid;
alter table transfer_workflow_events add column destination_account_id uuid;
alter table transfer_workflow_events add column amount numeric(19, 4);
alter table transfer_workflow_events add column currency varchar(3);
alter table transfer_workflow_events add column verified_at timestamp with time zone;
alter table transfer_workflow_events add column expires_at timestamp with time zone;
alter table transfer_workflow_events add column policy_version varchar(100);
