alter table transfer_workflows drop constraint ck_transfer_workflows_status;

alter table transfer_workflows add constraint ck_transfer_workflows_status check (
    status in ('PENDING', 'AWAITING_LEDGER_POSTING', 'AWAITING_RESERVATION_RELEASE', 'COMPLETED', 'FAILED', 'REVERSED'));
