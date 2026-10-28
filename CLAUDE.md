# Trading Dashboard: testing showcase

Maven multi-module monorepo (Java 25, Spring Boot 4.1) with five backend services, a React/Vite
dashboard, and layered tests. The main point of the repo is the **tests**, especially per-service
integration tests with Testcontainers + WireMock that need no deployed environment.

## Prerequisites
- JDK 25 with `JAVA_HOME` set (the root `pom.xml` targets `<java.version>25`), Maven, and a running Docker daemon.
- Don't install tools or add dependencies without asking the user first. Scripts are plain Java
  (`java File.java`), not Python.

## Layout
- `services/{auth,order,market-data,portfolio,notification}-service`: Spring Boot services
  (`web/` controllers + DTOs, `domain/` entities/repos, `service/`, `consume/` Kafka listeners,
  `events/` publishers, Flyway migrations in `src/main/resources/db/migration/<svc>/`)
- `common/`: shared `Jwt`, `ApiSecurityConfig` (JWT auth only, no roles), `ApiErrorHandler`, `Money`, `Clocks`, `MarketHours`
- `integration-tests/`: cross-service flow (services as subprocesses) · `e2e-tests/`: API/WS/UI against a running stack
- `frontend/web-dashboard/`: React + Vitest + WebdriverIO
- `docs/DESIGN.md`, `docs/testing-cookbook.md`, `docs/test-coverage.md`: read the cookbook before writing tests

## Commands
```bash
mvn clean install -DskipTests                       # build all modules
mvn -pl services/<svc> verify                       # unit + ArchUnit + *IT (Failsafe) + PMD + Checkstyle
mvn -pl services/<svc> verify -Dit.test=PlaceOrderIT -Dfailsafe.failIfNoSpecifiedTests=false
docker compose up --build -d && mvn -pl e2e-tests clean verify -Papi-e2e   # e2e needs the running stack
cd frontend/web-dashboard && npm run lint && npm run typecheck && npm run test:unit
```

## Test rules (enforced by PMD, ArchUnit, Checkstyle)
- Tests are linear (setup → action → assert): **no conditionals, boolean operators or loops** in test
  code, one scenario per test, `@ParameterizedTest` wherever cases differ only in data.
- Assert exact values, async via Awaitility (never `Thread.sleep`), time via an injected/mutable `Clock`.
  ITs must not use weak or non-deterministic assertions (`contains`, `doesNotContain`, ranges, `isNotNull`/
  `isNotBlank`, Hamcrest `containsString`, WireMock `containing`). `<Svc>IntegrationTestArchitectureTest`
  enforces this. Make the value deterministic instead (own owner per test, seed reset, fixed clock).
- `*IT` = integration test (Failsafe, `verify`); `*Test` = unit/ArchUnit (Surefire).
- Per-service ITs mock **only external communication** (WireMock HTTP/WS); Postgres/Kafka/Redis are real
  containers. Never mock internal beans.
- Never edit an existing Flyway migration (a hook blocks it); add `V<n+1>__*.sql`.

## Integration-test workflow
`/it-gen` generates, reviews and scores per-service ITs for **only the changed** services/endpoints
(agents `it-sme`, `it-writer`, `it-reviewer`; see `.claude/skills/it-gen/`). Target layout and rules
are in `.claude/skills/it-gen/conventions.md`. ITs live in `<service>/.../it/` (one class per
endpoint/listener, REST-Assured over RANDOM_PORT) with plumbing in `it/harness/`.

## Local environment notes
- Maven needs `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`.
- Docker runs in colima: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`,
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`.
