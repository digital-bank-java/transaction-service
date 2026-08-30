# Transaction Service

Transaction Service is the future owner of transfer orchestration and the
internal transfer saga/process-manager foundation for the Digital Bank Java
platform.

This bootstrap deliberately contains no transfer endpoint, account
reservation call, ledger posting, Kafka transport, PostgreSQL persistence, or
saga orchestration. Those behaviors belong to later implementation tasks.

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

## Build And Test

Run unit-phase tests:

```bash
./mvnw --batch-mode --no-transfer-progress test
```

Run the complete Maven lifecycle, including the random-port health
integration test:

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

The automated test context disables Config Client so it does not require a
running Config Server.

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

## Development Boundaries

Keep business workflows out of the bootstrap boundary. Future work may add
transfer commands, account reservation coordination, ledger posting events,
Kafka consumers/producers, persistence, and saga state management. Until
those tasks are approved, this repository should remain a deployable health
and configuration foundation.

CI runs Maven verification, strict Helm lint/render validation, and a
container smoke test using a small mock Config Server that supplies port
`8084`.
