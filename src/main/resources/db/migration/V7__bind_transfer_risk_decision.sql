alter table transfer_workflows
    add column customer_id varchar(100) not null default 'system';

alter table transfer_workflows
    add column channel varchar(30) not null default 'INTERNAL';

alter table transfer_workflows
    add column destination_class varchar(20) not null default 'INTERNAL';

alter table transfer_workflows
    add column risk_decision_id uuid;

alter table transfer_workflows
    add column risk_decision_request_id varchar(100);

alter table transfer_workflows
    add column risk_outcome varchar(30);

alter table transfer_workflows
    add column risk_reason_codes varchar(500);

alter table transfer_workflows
    add column risk_required_assurance varchar(30);

alter table transfer_workflows
    add column risk_challenge_type varchar(30);

alter table transfer_workflows
    add column risk_policy_version varchar(100);

alter table transfer_workflows
    add column risk_issued_at timestamp with time zone;

alter table transfer_workflows
    add column risk_expires_at timestamp with time zone;

update transfer_workflows
set risk_decision_request_id = transfer_request_id
where risk_decision_request_id is null;

alter table transfer_workflows
    alter column risk_decision_request_id set not null;

alter table transfer_workflows
    add constraint uq_transfer_workflows_risk_decision_id unique (risk_decision_id);

alter table transfer_workflows
    add constraint uq_transfer_workflows_risk_decision_request_id unique (risk_decision_request_id);

alter table transfer_workflows
    add constraint ck_transfer_workflows_destination_class check (
        destination_class in ('INTERNAL', 'DOMESTIC', 'INTERNATIONAL'));

alter table transfer_workflows
    add constraint ck_transfer_workflows_risk_outcome check (
        risk_outcome is null or risk_outcome in ('ALLOW', 'REQUIRE_STEP_UP', 'DECLINE'));
