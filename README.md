# Transaction Service

Transaction Service is the Digital Bank Java platform bootstrap for the future
transfer orchestration and internal transfer saga/process-manager boundary.

## Implemented State

The current repository provides a deployable Spring Boot service foundation:

- Runs on Java 21 with service name `transaction-service`.
- Loads runtime configuration from Spring Cloud Config Server.
- Exposes Actuator health, liveness, and readiness endpoints.
- Builds a non-root container image and deploys through a hardened Helm chart.
- Supports the `8084` service port supplied by runtime configuration.
- Owns a transport-neutral transfer saga/process-manager application boundary.
- Persists transfer workflow state, consumed-event status, and deterministic
  next actions with optimistic locking.

There is no public transfer API and the service does not directly mutate
account balances or ledger entries.

## Transfer Saga Foundation

The current transfer workflow boundary supports the first internal-transfer
coordination slice:

1. `RequestTransfer` creates a `PENDING` workflow and records an account
   reservation request.
2. `AccountReservationCreated` advances the workflow to
   `AWAITING_LEDGER_POSTING` and records a ledger-posting request.
3. `LedgerPostingCompleted` marks the transfer `COMPLETED`.
4. `LedgerPostingFailed` marks it `FAILED` and records an explicit reservation
   release action.
5. `AccountReservationRejected` marks a pending transfer `FAILED`.

Every message carries a transfer id, correlation id, and event/request id.
Duplicate messages are idempotent. Ledger outcomes received before reservation
success are durably deferred and replayed after the reservation event arrives.
Workflow rows use optimistic locking, while inbox event ids and deterministic
action ids prevent duplicate work during retries.

The application boundary uses typed Java records and ports. It intentionally
does not add HTTP endpoints, Kafka dependencies, concrete topics, schema
registry configuration, or transport adapters. Future adapters may map these
messages to governed platform contracts, including
`AccountReservationCreated`, `LedgerPostingCompleted`, and
`LedgerPostingFailed`.

The PostgreSQL schema contains `transfer_workflows`,
`transfer_workflow_events`, and `transfer_workflow_actions`. Account Service
remains the owner of reservations and account projections; Ledger Service
remains the owner of immutable financial postings.

## Remaining Transfer Scope

The following capabilities remain outside this foundation:

- HTTP transfer API and inbound adapters.
- Account reservation execution and account balance projection updates.
- Ledger posting execution and immutable ledger entry ownership.
- Kafka producers/consumers, concrete topics, schema registry wiring, and
  outbox publication.
- Reversal orchestration, reconciliation, and end-to-end SIT event evidence.

Do not add an endpoint, gateway route, topic, or transport schema here until
the corresponding work is approved and tracked.

## Responsibilities And Boundaries

Current responsibilities include the service bootstrap, runtime health,
configuration-client integration, transfer workflow orchestration, durable
workflow state, packaging, and deployment foundation.

The service does not own customer data, account data, ledger entries, account
balance projections, Kafka infrastructure, or secrets. The
configuration repository is a separate repository owned by the Config Server
workflow; this repository only consumes configuration exposed for
`transaction-service`.

## Runtime Configuration

The service is a Spring Cloud Config Client. Config Server supplies the
effective runtime configuration, including the application port.

| Variable | Purpose | Default |
| --- | --- | --- |
| `CONFIG_SERVER_URL` | Config Server base URL | `http://localhost:8888` |
| `SPRING_PROFILES_ACTIVE` | Runtime environment profile | Spring `default` profile |

Config Server must expose the `transaction-service` configuration. Do not
commit secrets or environment-specific credentials to this repository, image,
or Helm values.

## Environments

The platform has three formal runtime environments:

| Profile | Purpose |
| --- | --- |
| `sit` | Integrated development and testing in the local or hosted SIT platform. |
| `uat` | User acceptance testing. |
| `prod` | Production. |

SIT is the supported lowest environment for this bootstrap. A workstation JVM
used for debugging connects to forwarded SIT dependencies and uses SIT
configuration; it is not a fourth `local` environment or deployment profile.
The same application artifact is intended to be promoted through `sit`, `uat`,
and `prod` without rebuilding. UAT and PROD are intended for AWS-hosted
deployment. Their runtime configuration and secrets are delivered by the
platform deployment mechanism and are not committed to this repository.

## Prerequisites

- Java 21.
- Network access to Maven Central for the initial dependency download.
- A running Config Server for normal application startup.
- Docker Desktop for image builds.
- Helm 4 and Docker Desktop Kubernetes for local SIT deployment.

The Maven Wrapper is included, so a global Maven installation is not needed.

```bash
java -version
./mvnw --version
docker version
helm version --short
kubectl config current-context
```

## Workstation Debugging Against SIT

Workstation debugging is a temporary JVM process against SIT, not a separate
environment. Use the shared [workstation debugging procedure](https://github.com/digital-bank-java/.github/blob/main/docs/workstation-debugging-against-sit.md)
for the required port forwards, temporary overrides, and cleanup steps.

After the documented SIT dependencies and overrides are available, start the
service with:

```bash
./mvnw spring-boot:run
```

Verify the local process on the configured service port:

```bash
curl --fail http://localhost:8084/actuator/health
```

## Build And Test

Run unit-phase tests:

```bash
./mvnw --batch-mode --no-transfer-progress test
```

Run integration tests and package verification after the unit phase:

```bash
./mvnw --batch-mode --no-transfer-progress verify -DskipUnitTests=true
```

The automated test context disables Config Client so it does not require a
running Config Server.

## CI Validation

The repository CI workflow runs the Maven test job and Helm validation job in
parallel. The container build and smoke-test job waits for both jobs to pass.
The workflow includes these checks:

1. Unit-test stage:

   ```bash
   ./mvnw --batch-mode --no-transfer-progress test
   ```

2. Integration and package verification stage:

   ```bash
   ./mvnw --batch-mode --no-transfer-progress verify -DskipUnitTests=true
   ```

3. Helm validation stage:

   ```bash
   helm lint helm --strict --values helm/values-sit.yaml

   helm template transaction-service helm \
     --namespace digital-bank-sit \
     --values helm/values-sit.yaml \
     --set image.tag="${GITHUB_SHA}" \
     > rendered.yaml
   test -s rendered.yaml
   ```

4. Container build and smoke stage: build
   `digital-bank-java/transaction-service:ci`, verify its configured user is
   `10001:10001`, run it against a disposable mock Config Server with a
   read-only root filesystem and writable `/tmp`, then poll:

   ```bash
   docker run --detach \
     --name transaction-service-ci \
     --read-only \
     --tmpfs /tmp:rw,size=64m \
     --publish 8084:8084 \
     --add-host host.docker.internal:host-gateway \
     --env CONFIG_SERVER_URL=http://host.docker.internal:8888 \
     digital-bank-java/transaction-service:ci

   curl --fail http://localhost:8084/actuator/health
   ```

Third-party GitHub Actions are pinned to immutable commit SHAs. The CI mock
Config Server supplies only the non-sensitive `server.port` setting.

## Run With Docker

Build the deployable image:

```bash
docker build -t digital-bank-java/transaction-service:0.0.1 .
```

Run it against Config Server reachable from the host:

```bash
docker run --rm \
  --name digital-bank-transaction-service \
  --publish 8084:8084 \
  --env CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  --env SPRING_PROFILES_ACTIVE=sit \
  digital-bank-java/transaction-service:0.0.1
```

The runtime image runs as numeric non-root user and group `10001:10001` and
uses `/tmp` for writable temporary files.

## Deploy To Local SIT

Local SIT runs in the `digital-bank-sit` namespace. The Config Server release
must already be healthy there; the chart uses its in-cluster address
`http://config-server:8888` and activates the `sit` profile.

Validate the chart and rendered Kubernetes resources:

```bash
helm lint helm --strict

helm template transaction-service helm \
  --values helm/values-sit.yaml \
  --namespace digital-bank-sit | kubectl apply --dry-run=client -f -
```

Install or upgrade the release:

```bash
helm upgrade --install transaction-service helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --values helm/values-sit.yaml \
  --wait \
  --timeout 5m
```

Inspect rollout and service health:

```bash
kubectl rollout status deployment/transaction-service \
  --namespace digital-bank-sit --timeout=180s

kubectl port-forward service/transaction-service 18084:8084 \
  --namespace digital-bank-sit
```

In another terminal:

```bash
curl --fail http://localhost:18084/actuator/health
curl --fail http://localhost:18084/actuator/health/liveness
curl --fail http://localhost:18084/actuator/health/readiness
```

The chart deploys one internal `ClusterIP` service with a read-only root
filesystem, non-root security context, no privilege escalation, no Linux
capabilities, and an `emptyDir` mount for `/tmp`.

## Contribution Workflow

Every change must have a tracked issue, a dedicated branch, and a non-draft
pull request. Do not commit directly to `main`.

1. Create or identify the tracked issue describing the change.
2. Create a dedicated branch from the current base branch, using the issue in
   the branch name where practical.
3. Implement the scoped change and run the documented CI commands locally.
4. Open a non-draft pull request that links the tracked issue, for example
   with `Closes #<issue-number>` when the issue is in this repository.
5. Wait for required reviewers and all CI stages before merging.

See the organization [README standard](https://github.com/digital-bank-java/.github/blob/main/docs/readme-standard.md)
and [platform conventions](https://github.com/digital-bank-java/.github/blob/main/docs/platform-conventions.md).
