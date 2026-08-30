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

There are no transfer APIs or transaction side effects in this bootstrap.

## Planned Transfer And Saga Behavior

The following capabilities are planned and are not implemented here:

- Transfer commands and transfer orchestration.
- Account reservation coordination.
- Ledger posting or balance mutation.
- Kafka producers, consumers, topics, or event contracts owned by this service.
- PostgreSQL persistence and saga state management.

Do not document or add an endpoint, integration, topic, database, or gateway
route for these capabilities until the corresponding work is approved and
tracked.

## Responsibilities And Boundaries

Current responsibilities are limited to the service bootstrap, runtime health,
configuration-client integration, packaging, and deployment foundation.

The service does not currently own customer data, account data, ledger entries,
transfer execution, Kafka infrastructure, persistence, or secrets. The
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
and `prod` without rebuilding. Environment-specific configuration and secrets
remain outside this repository.

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

The repository CI workflow runs these stages in order:

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

## Planned Scope

Keep business workflows out of the bootstrap boundary. Future work may add
transfer commands, account reservation coordination, ledger posting events,
Kafka consumers/producers, persistence, and saga state management. Until
those tasks are approved, this repository should remain a deployable health
and configuration foundation.

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
