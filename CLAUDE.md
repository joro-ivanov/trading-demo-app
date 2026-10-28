# Trading Dashboard: testing showcase

Maven multi-module monorepo (Java 25, Spring Boot 4.1) with five backend services, a React/Vite
dashboard, and layered tests. The main point of the repo is the **tests**, especially per-service
integration tests with Testcontainers + WireMock that need no deployed environment.

## Prerequisites
- JDK 25 with `JAVA_HOME` set (the root `pom.xml` targets `<java.version>25`), Maven, and a running Docker daemon.
- Don't install tools or add dependencies without asking the user first. Scripts are plain Java
  (`java File.java`), not Python.

## Layout
- `services/{auth,order,market-data,portfolio,notification,alert,watchlist,risk}-service`: Spring Boot services
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
- **No magic values in any test** (all modules, e2e included): every string and number is a semantically
  named constant or local variable, or an annotation data-table entry (`@CsvSource`). `0`, `1`, `""` and
  `Allure.step`/assertion-description prose are allowed inline. PMD `build/pmd/no-magic-values.xml` enforces it.
  ITs must not use weak or non-deterministic assertions (`contains`, `doesNotContain`, ranges, `isNotNull`/
  `isNotBlank`, Hamcrest `containsString`, WireMock `containing`). `<Module>TestAssertionsArchitectureTest`
  enforces this in every backend module. Make the value deterministic instead (own owner per test, seed reset, fixed clock).
- `*IT` = integration test (Failsafe, `verify`); `*Test` = unit/ArchUnit (Surefire).
- Per-service ITs mock **only external communication** (WireMock HTTP/WS); Postgres/Kafka/Redis are real
  containers. Never mock internal beans.
- Never edit an existing Flyway migration (a hook blocks it); add `V<n+1>__*.sql`.

## When a check or test fails: report it, never hide it
Applies to Checkstyle, PMD, ArchUnit, TestLint and every functional test (unit, per-service IT,
flagship, e2e). Don't change a test or a rule to make a failure go away. A failing check can be a
real defect, and hiding it sweeps the defect under the rug.
- **Fix it directly only if the defect is in the test code itself** (it doesn't compile, an unused
  import, a wrong helper, a stub missing for a new dependency) **and** the fix doesn't change what is
  verified. Say what you fixed.
- **Otherwise stop and ask the user**, presenting:
  - what the test verifies, and on what basis (code line, case or doc)
  - the exact failure (expected vs actual, or the rule and message, with output)
  - your analysis of why it fails
  - the options
- **Never do these without the user's decision:**
  - change an expected value to the observed one
  - weaken, remove or skip an assertion or case, or `@Disabled`
  - suppress a rule (`@SuppressWarnings("PMD…")`, `// NOPMD`, `CHECKSTYLE:OFF`, ArchUnit freezing)
  - edit `build/pmd`, `build/checkstyle` or the ArchUnit rules
  - change production code just to turn a test green
  - silence logs (log levels, removed appenders, swallowed exceptions)
- **Green isn't the end of the check: read the logs too.** For backend runs, pass
  `-Dmaven.test.redirectTestOutputToFile=true` and run
  `java .claude/skills/it-gen/scripts/LogScan.java services/<svc>/target/failsafe-reports services/<svc>/target/container-logs`.
  Container logs (Postgres, Kafka, Redis) are captured by Testcontainers' `Slf4jLogConsumer` into
  `target/container-logs/<name>.log`. Report every ERROR, WARN or exception that no test deliberately
  provokes, the same way as a failure.

## HTTP 500 is never expected
A 500 means a bug. Outages are 503 (dependency or database unreachable / timed out) and bad upstream
answers are 502. Every outbound client uses `common.http.HttpClients.withTimeouts(...)` (explicit connect /
read timeouts from a validated `*Properties` record, so a hung dependency becomes a 503, not a hung request)
and translates failures with `common.http.Downstream`. `common.security.ApiErrorHandler` is the
safety net. No code may produce a 500 on purpose and no test may expect one: PMD
`build/pmd/no-internal-server-error.xml`, ArchUnit `neverProduceInternalServerError` and TestLint enforce
it. A 500 seen at runtime (LogScan `unhandledHttp500: true`) is a product defect: report it, don't assert it.

## Integration-test workflow
`/it-gen` generates, reviews and scores per-service ITs for **only the changed** services/endpoints
(agents `it-sme`, `it-writer`, `it-reviewer`; see `.claude/skills/it-gen/`). Target layout and rules
are in `.claude/skills/it-gen/conventions.md`. ITs live in `<service>/.../it/` (one class per
endpoint/listener, REST-Assured over RANDOM_PORT) with plumbing in `it/harness/`.

## Local environment notes
- Maven needs `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`.
- Docker runs in colima: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`,
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`.
