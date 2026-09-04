# Transfer Risk Gate SIT Verification

This verification exercises the Sprint 4 transfer-risk decision boundary. It
does not claim that MFA, account reservation, or ledger posting is complete.

## Expected Contract

`POST /internal/v1/transfer-workflows` evaluates the authenticated transfer
subject and normalized intent before any reservation action is recorded.

| Outcome | Workflow state | Reservation action |
| --- | --- | --- |
| `ALLOW` | `PENDING` | Recorded |
| `REQUIRE_STEP_UP` | `PENDING` | Not recorded |
| `DECLINE` | `FAILED` | Not recorded |

The response includes `riskDecisionId`, `riskDecisionRequestId`,
`riskOutcome`, `riskReasonCodes`, `riskPolicyVersion`, `riskIssuedAt`, and
`riskExpiresAt`. A repeated request with the same transfer and decision
request identifiers is idempotent. A changed transfer intent using an already
persisted request identity returns `409` Problem Details.

## SIT Preparation

Confirm the Transaction Service rollout uses the intended risk configuration:

```bash
kubectl -n digital-bank-sit get deployment transaction-service \
  -o jsonpath='{.spec.template.spec.containers[0].env}'
kubectl -n digital-bank-sit rollout status deployment/transaction-service \
  --timeout=180s
```

Use an authorized SIT JWT with the `transfer.internal` scope. Keep the token
outside exported Insomnia workspaces and shell history where practical.

## Allow Example

Use `destinationClass: INTERNAL` and an amount below the configured threshold.
The response should be `201`, contain `riskOutcome: ALLOW`, and contain one
`REQUEST_ACCOUNT_RESERVATION` action.

## Step-Up Example

Use `destinationClass: INTERNATIONAL` or an amount at least `10000` AED. The
response should be `201`, contain `riskOutcome: REQUIRE_STEP_UP`, remain
`PENDING`, and contain an empty `actions` array. No reservation request should
be emitted. The subsequent MFA challenge is future Sprint 4 work.

## Decline Example

Configure a destination class in
`TRANSACTION_RISK_DECLINED_DESTINATION_CLASSES` only for a controlled SIT
test. The response should be `201`, contain `riskOutcome: DECLINE`, have
status `FAILED`, and contain no reservation action.

## Persistence Check

Through the SIT PostgreSQL connection, inspect the workflow row without
editing it:

```sql
select id,
       customer_id,
       channel,
       destination_class,
       risk_decision_id,
       risk_decision_request_id,
       risk_outcome,
       risk_reason_codes,
       risk_policy_version,
       risk_issued_at,
       risk_expires_at,
       status,
       version
from transfer_workflows
order by created_at desc;
```

Verify that the risk decision is stored with the workflow and that a
step-up/decline request has no corresponding reservation action:

```sql
select action_id, transfer_id, action_type, request_id
from transfer_workflow_actions
where transfer_id = '<transfer-id>'
order by created_at;
```

Do not update or delete workflow rows directly. The workflow and risk
decision snapshot are application-owned state; later ledger reconciliation
will compare resulting financial facts separately.
