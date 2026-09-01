alter table transfer_workflow_events
    add constraint fk_transfer_workflow_events_workflow
    foreign key (transfer_id) references transfer_workflows (id);

alter table transfer_workflow_actions
    add constraint fk_transfer_workflow_actions_workflow
    foreign key (transfer_id) references transfer_workflows (id);
