# Transaction Service Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a deployable Java 21 Transaction Service foundation for the future internal-transfer saga, without implementing transfer behavior yet.

**Architecture:** The service follows the established Spring Boot pattern used by account-service and ledger-service. It has only a bootstrap application boundary: Config Server supplies the runtime port and environment configuration, Actuator exposes Kubernetes probes, Docker packages a non-root runtime, and Helm deploys it to local SIT. Domain, persistence, HTTP business APIs, Kafka producers/consumers, and saga orchestration stay out of this story.

**Tech Stack:** Java 21, Spring Boot 4.0.7, Spring Cloud Config Client 2025.1.2, Spring MVC, Actuator, Springdoc OpenAPI, Maven Wrapper, Docker, Helm 4, Kubernetes, GitHub Actions.

## Global Constraints

- Repository package name is `com.digitalbank.transactionservice`; artifact and application name are `transaction-service`.
- Runtime configuration is consumed from Config Server/config-repo, never hard-coded per environment in this repository.
- Local SIT uses Docker Desktop Kubernetes namespace `digital-bank-sit`.
- The container must run as UID/GID `10001`, with a read-only root filesystem and writable `/tmp` volume.
- `./mvnw test` runs unit tests; `./mvnw verify` is the complete local quality gate.
- No transfer state, reservation, ledger posting, PostgreSQL schema, Kafka event, or public API belongs in this bootstrap story.
- All behavior changes require a focused test before production code is added.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `.gitattributes` | Preserve LF for Maven shell scripts and CRLF for `mvnw.cmd`. |
| `.gitignore` | Ignore operating-system, IDE, Maven, and generated build files. |
| `.github/CODEOWNERS` | Require platform-owner review for all paths. |
| `.github/workflows/ci.yml` | Run Maven verification, Helm validation, and container smoke validation in GitHub Actions. |
| `pom.xml` | Declare Java, Spring Boot, Config Client, Actuator, web, validation, OpenAPI, test phases, and build plugins. |
| `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd` | Provide repository-pinned Maven execution. |
| `src/main/java/com/digitalbank/transactionservice/TransactionServiceApplication.java` | Spring Boot entry point only. |
| `src/main/resources/application.properties` | Application name, Config Server import, and health-probe exposure. |
| `src/test/java/com/digitalbank/transactionservice/TransactionServiceApplicationIT.java` | Start the application on a random port and assert Actuator health is UP. |
| `src/test/resources/application.properties` | Disable Config Client in the local automated test context. |
| `Dockerfile` | Build the executable JAR and run it as non-root. |
| `.dockerignore` | Keep the Docker build context small and exclude local/editor artifacts. |
| `helm/Chart.yaml` | Identify the deployable Helm chart and independent chart version. |
| `helm/values.yaml` | Safe common deployment defaults. |
| `helm/values-sit.yaml` | SIT profile and in-cluster Config Server address. |
| `helm/templates/_helpers.tpl` | Reusable chart naming and Kubernetes label definitions. |
| `helm/templates/deployment.yaml` | Hardened single-replica Kubernetes deployment with Config Server environment variables and health probes. |
| `helm/templates/service.yaml` | ClusterIP HTTP service on port 8084. |
| `README.md` | Human setup, verification, Docker, and SIT rollout guidance. |
| `AGENTS.md` | Repository-specific boundaries, commands, testing, and delivery guidance. |

## Task 1: Establish the Maven Service Skeleton

**Files:**

- Create: `.gitattributes`
- Create: `.gitignore`
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Create: `mvnw`
- Create: `mvnw.cmd`
- Create: `pom.xml`
- Create: `src/main/java/com/digitalbank/transactionservice/TransactionServiceApplication.java`
- Create: `src/main/resources/application.properties`

**Consumes:** The organization Java-service baseline and the Config Server contract at `/${spring.application.name}/{profile}`.

**Produces:** An executable Spring Boot application named `transaction-service` that imports runtime configuration from `${CONFIG_SERVER_URL:http://localhost:8888}` and exposes Actuator health probes.

- [ ] **Step 1: Generate the Maven Wrapper and create the initial dependency model.**

  Use Spring Boot `4.0.7`, Java `21`, Spring Cloud dependency management `2025.1.2`, and declare these runtime dependencies:

  ```xml
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.2</version>
  </dependency>
  ```

  Keep database, Flyway, Kafka, Testcontainers, and persistence dependencies out of this task.

- [ ] **Step 2: Add the minimal application and runtime configuration.**

  ```java
  package com.digitalbank.transactionservice;

  import org.springframework.boot.SpringApplication;
  import org.springframework.boot.autoconfigure.SpringBootApplication;

  @SpringBootApplication
  public class TransactionServiceApplication {
      public static void main(String[] args) {
          SpringApplication.run(TransactionServiceApplication.class, args);
      }
  }
  ```

  ```properties
  spring.application.name=transaction-service
  spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}

  management.endpoints.web.exposure.include=health,info
  management.endpoint.health.probes.enabled=true
  ```

- [ ] **Step 3: Confirm the project compiles before adding behavioral tests.**

  Run: `./mvnw --batch-mode --no-transfer-progress compile`

  Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit the bootstrap skeleton.**

  ```bash
  git add .gitattributes .gitignore .mvn mvnw mvnw.cmd pom.xml src/main
  git commit -m "feat: bootstrap transaction service"
  ```

## Task 2: Prove the Runtime Health Contract

**Files:**

- Create: `src/test/resources/application.properties`
- Create: `src/test/java/com/digitalbank/transactionservice/TransactionServiceApplicationIT.java`
- Modify: `pom.xml`

**Consumes:** `TransactionServiceApplication` and Actuator's HTTP health endpoint.

**Produces:** A Failsafe integration test proving the bootstrapped application starts and returns `UP` without depending on a running Config Server.

- [ ] **Step 1: Write the failing integration test.**

  ```java
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  class TransactionServiceApplicationIT {
      @LocalServerPort
      private int port;

      @Test
      void healthEndpointReportsUp() throws Exception {
          var response = HttpClient.newHttpClient().send(
                  HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                          .GET()
                          .build(),
                  HttpResponse.BodyHandlers.ofString());

          assertThat(response.statusCode()).isEqualTo(200);
          assertThat(response.body()).contains("\"status\":\"UP\"");
      }
  }
  ```

  Configure test properties with:

  ```properties
  spring.application.name=transaction-service
  spring.cloud.config.enabled=false
  ```

- [ ] **Step 2: Run the test and verify the expected initial failure.**

  Run: `./mvnw -Dtest=TransactionServiceApplicationIT test`

  Expected: the build fails because integration tests are not yet configured for the `verify` lifecycle or the test is not discovered as a Failsafe integration test.

- [ ] **Step 3: Configure Maven test phases.**

  Configure Surefire to include `*Test.java` and `*Tests.java` while excluding `*IT.java`, `*IntegrationTest.java`, and `*IntegrationTests.java`. Configure Failsafe to include those integration-test suffixes and bind `integration-test` and `verify` goals. Retain a `skipUnitTests` property defaulted to `false`.

- [ ] **Step 4: Run the integration test through the correct lifecycle.**

  Run: `./mvnw --batch-mode --no-transfer-progress verify`

  Expected: `TransactionServiceApplicationIT.healthEndpointReportsUp` passes and the build reports `BUILD SUCCESS`.

- [ ] **Step 5: Commit the tested health contract.**

  ```bash
  git add pom.xml src/test
  git commit -m "test: verify transaction service health"
  ```

## Task 3: Package the Service for Container and Kubernetes Execution

**Files:**

- Create: `.dockerignore`
- Create: `Dockerfile`
- Create: `helm/Chart.yaml`
- Create: `helm/values.yaml`
- Create: `helm/values-sit.yaml`
- Create: `helm/templates/_helpers.tpl`
- Create: `helm/templates/deployment.yaml`
- Create: `helm/templates/service.yaml`

**Consumes:** The executable JAR from Task 1 and runtime properties from Config Server.

**Produces:** A local image `digital-bank-java/transaction-service:0.0.1` and Helm chart exposing in-cluster `transaction-service:8084`.

- [ ] **Step 1: Render a deliberately incomplete Helm chart and observe validation failure.**

  Create `helm/Chart.yaml` and use a Deployment that references a missing helper name. Then run:

  ```bash
  helm lint helm --strict
  helm template transaction-service helm --values helm/values-sit.yaml --namespace digital-bank-sit
  ```

  Expected: template rendering fails with a missing template/helper error.

- [ ] **Step 2: Add the reusable Helm helper definitions.**

  Define `transaction-service.name`, `transaction-service.fullname`, `transaction-service.selectorLabels`, and `transaction-service.labels`. Use these consistently in the Deployment and Service so the Service selector always matches pod labels.

- [ ] **Step 3: Add the hardened Deployment and Service.**

  Use these runtime values:

  ```yaml
  image:
    repository: digital-bank-java/transaction-service
    tag: "0.0.1"
    pullPolicy: IfNotPresent

  runtime:
    profile: default
    configServerUrl: http://config-server:8888

  service:
    type: ClusterIP
    port: 8084
  ```

  The Deployment must set `CONFIG_SERVER_URL` and `SPRING_PROFILES_ACTIVE`, mount an `emptyDir` at `/tmp`, run as UID/GID `10001`, disable privilege escalation, drop all Linux capabilities, and use startup/liveness/readiness HTTP probes against Actuator health groups.

- [ ] **Step 4: Add a multi-stage, non-root Dockerfile.**

  The builder image runs `./mvnw --batch-mode clean package -DskipTests`. The runtime image copies only `transaction-service-*.jar`, creates the `spring` user with UID/GID `10001`, exposes `8084`, and uses:

  ```dockerfile
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```

- [ ] **Step 5: Validate rendered Kubernetes resources and the image user.**

  Run:

  ```bash
  helm lint helm --strict
  helm template transaction-service helm \
    --values helm/values-sit.yaml \
    --namespace digital-bank-sit | kubectl apply --dry-run=client -f -
  docker build -t digital-bank-java/transaction-service:0.0.1 .
  docker image inspect --format '{{.Config.User}}' digital-bank-java/transaction-service:0.0.1
  ```

  Expected: Helm commands pass and the image user is `10001:10001`.

- [ ] **Step 6: Commit the delivery artifacts.**

  ```bash
  git add .dockerignore Dockerfile helm
  git commit -m "feat: package transaction service for SIT"
  ```

## Task 4: Make the Bootstrap Repeatable for Humans and CI

**Files:**

- Create: `.github/CODEOWNERS`
- Create: `.github/workflows/ci.yml`
- Create: `AGENTS.md`
- Modify: `README.md`

**Consumes:** Maven verification, Helm chart, Dockerfile, and Config Server contract from prior tasks.

**Produces:** A documented and CI-validated bootstrap that a new contributor can build, test, package, deploy, and verify without relying on unstated workstation knowledge.

- [ ] **Step 1: Add CI jobs that mirror local quality gates.**

  Add GitHub Actions jobs for:

  1. Maven verification using Java 21 and `./mvnw --batch-mode --no-transfer-progress verify`.
  2. `helm lint helm --strict` plus a rendered chart check using an immutable `${GITHUB_SHA}` image tag.
  3. Container build plus smoke test with a tiny mock Config Server that returns `server.port: 8084`; run the container with `CONFIG_SERVER_URL=http://host.docker.internal:8888` and poll `/actuator/health`.

- [ ] **Step 2: Run an intentional CI configuration failure locally.**

  Temporarily change the smoke-test expected port from `8084` to `9999` in a local uncommitted edit and run the relevant shell section or inspect the container’s failure. Restore the file before proceeding.

  Expected: the smoke test cannot reach the expected health endpoint, proving the CI check detects an incorrect Config Server contract.

- [ ] **Step 3: Restore the correct configuration and validate all local quality gates.**

  Run:

  ```bash
  ./mvnw --batch-mode --no-transfer-progress verify
  helm lint helm --strict
  helm template transaction-service helm \
    --values helm/values-sit.yaml \
    --namespace digital-bank-sit | kubectl apply --dry-run=client -f -
  git diff --check
  ```

  Expected: all commands exit successfully.

- [ ] **Step 4: Write operational documentation.**

  `README.md` must state that Transaction Service is the future transfer-saga/process-manager owner, but this story intentionally has no transfer endpoint, persistence, or Kafka behavior. Document local commands, Docker build, Helm render/deployment, health verification, and the future AWS mapping. `AGENTS.md` must state the same architectural boundaries for contributors and agents.

- [ ] **Step 5: Commit the documented and automated bootstrap.**

  ```bash
  git add .github AGENTS.md README.md
  git commit -m "docs: document transaction service bootstrap"
  ```

## Task 5: Validate Local SIT Deployment

**Files:**

- Modify: `helm/values-sit.yaml` only if rendered validation proves an SIT override is missing.

**Consumes:** Local Docker image, Config Server, and `digital-bank-sit` namespace.

**Produces:** One ready `transaction-service` pod reachable by its ClusterIP service and verified through a temporary port-forward or in-cluster curl.

- [ ] **Step 1: Deploy the locally built image.**

  ```bash
  helm upgrade --install transaction-service helm \
    --namespace digital-bank-sit \
    --create-namespace \
    --values helm/values-sit.yaml \
    --wait \
    --timeout 5m
  ```

- [ ] **Step 2: Verify the rollout and service health.**

  ```bash
  kubectl rollout status deployment/transaction-service \
    --namespace digital-bank-sit \
    --timeout=180s
  kubectl get pods --namespace digital-bank-sit --selector app.kubernetes.io/name=transaction-service
  kubectl port-forward --namespace digital-bank-sit svc/transaction-service 8084:8084
  ```

  In a second terminal:

  ```bash
  curl --fail http://localhost:8084/actuator/health
  ```

  Expected: the Deployment has one ready pod and the health response reports `UP`.

- [ ] **Step 3: Commit any proven SIT-only correction.**

  ```bash
  git add helm/values-sit.yaml
  git commit -m "fix: align transaction service SIT configuration"
  ```

  Do not create an empty correction commit if deployment succeeds without a configuration change.

## Final Verification and Pull Request

- [ ] **Step 1: Run the complete local quality gate.**

  ```bash
  ./mvnw --batch-mode --no-transfer-progress verify
  helm lint helm --strict
  helm template transaction-service helm \
    --values helm/values-sit.yaml \
    --namespace digital-bank-sit | kubectl apply --dry-run=client -f -
  docker build -t digital-bank-java/transaction-service:0.0.1 .
  git diff --check
  git status
  ```

- [ ] **Step 2: Open the implementation pull request.**

  Use a branch named `feature/1-bootstrap-transaction-service`. The PR description must contain `Closes #1`, state that no saga or transfer business behavior was added, and link any related `config-repo`, `infra-sit`, or API Gateway PRs if those become necessary. State the preferred merge order and rollout steps explicitly if cross-repository configuration is introduced.

## Plan Self-Review

- Spec coverage: Tasks 1 through 5 cover every story requirement: Java foundation, Config Client, Actuator, architecture boundary, health integration test, Docker, Helm, CI, CODEOWNERS, README, SIT deployment, and verification.
- Intentional exclusions: persistence, Flyway, transaction APIs, Kafka, reservation management, ledger posting, and saga behavior are explicitly postponed to their dedicated stories.
- Placeholder scan: no unresolved requirements or ambiguous environment ownership remain.
- Type consistency: service name, Java package, port `8084`, image name, Helm release, Config Server lookup name, and Kubernetes Service name are aligned throughout.
