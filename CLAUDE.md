# Trading Dashboard: testing showcase

Maven multi-module monorepo (Java 25, Spring Boot 4.1) with eight backend services, a React/Vite
dashboard, and layered tests. The main point of the repo is the **tests**, especially per-service
integration tests with Testcontainers + WireMock that need no deployed environment.

## Prerequisites
- JDK 25 with `JAVA_HOME` set (the root `pom.xml` targets `<java.version>25`), Maven, and a running Docker daemon.
- Don't install tools or add dependencies without asking the user first. Scripts are plain Java
  (`java File.java`), not Python.

## Layout
- `services/{auth,order,market-data,portfolio,notification,alert,watchlist,risk}-service`: Spring Boot services
  (`web/` controllers + DTOs, `domain/` entities/repos, `service/`, `consume/` Kafka listeners,
  `events/` publishers, Flyway migrations in `src/main/resources/db/migration/<svc>/`);
  `services/pom.xml` is their parent (shared deps, service PMD rulesets, JaCoCo, boot jar)
- `common/`: shared `Jwt`, `ApiSecurityConfig` (JWT auth only, no roles), `ApiErrorHandler`, `Money`, `Clocks`, `MarketHours`
- `testing/test-rules`: the shared ArchUnit rule libraries (`TestAssertionRules`, `IntegrationTestRules`,
  `ServiceArchitectureRules`) and the only `@RawJsonPayload` · `testing/test-support`: shared IT plumbing
  (`ServiceITBase`, `TestContainers`, `KafkaProbe`, `TestJwt`, `MutableClock`, …), test scope
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

## Test rules
The rules and which tool enforces each are owned by
[`.claude/skills/it-gen/conventions.md`](.claude/skills/it-gen/conventions.md) ("Test-method rules",
"Enforced by"). They apply to all test code in every module, e2e included. In short:
- Tests are linear (setup → action → assert): **no conditionals, boolean operators or loops**, one
  scenario per test, `@ParameterizedTest` wherever cases differ only in data (rules 1–3).
- **Assert exact values**, no `contains`/ranges/`isNotNull`-style checks. Make the value deterministic
  instead of weakening the assertion (rule 4 lists how).
- Async via Awaitility (never `Thread.sleep`), time via an injected/mutable `Clock`, timeout stubs
  ≥ 3× the read timeout (rule 5).
- **No magic values**: every string and number is a named constant, a local variable or an annotation
  data-table entry; `0`, `1`, `""` and description prose are allowed inline (rule 6 lists exactly where).
- **DTOs only**, never hand-built or hand-read JSON, except in a `@RawJsonPayload(reason = "…")`
  invalid-payload method (rule 7). `/it-gen` scores a violation 0.
- Per-service ITs mock **only external communication** (WireMock HTTP/WS); Postgres/Kafka/Redis are
  real containers. Never mock internal beans (rule 8).
- `*IT` = integration test (Failsafe, `verify`); `*Test` = unit/ArchUnit (Surefire).
- Shared test plumbing lives in `testing/test-support`; reuse it instead of copying.
- Never edit an existing Flyway migration (a hook blocks it); add `V<n+1>__*.sql`.

## When a check or test fails: report it, never hide it
Applies to Checkstyle, PMD, ArchUnit and every functional test (unit, per-service IT, flagship,
e2e), and to the logs of those runs. Don't change a test or a rule to make a failure go away. A
failing check can be a real defect, and hiding it sweeps the defect under the rug.
- **Fix it directly only if the defect is in the test code itself** (it doesn't compile, an unused
  import, a wrong helper, logic in a test that belongs in `harness/`, a stub missing for a new
  dependency) **and** the fix doesn't change what is verified. Say what you fixed.
- **Otherwise stop on that case, leave the test as written, and ask the user.** That includes the
  product behaving differently from the intended behaviour, a test that existed before now failing,
  and a rule that could only be satisfied by changing what is asserted. Present:
  - what the test verifies, and on what basis (code line, case or doc)
  - the exact failure (expected vs actual, or the rule and message, with output)
  - your analysis of why it fails (product defect, ambiguous or undocumented behaviour, a wrong test
    assumption, or environment)
  - the options (e.g. fix the product, confirm the behaviour and update the expectation, track it as
    a known issue)
- **Never do these without the user's decision:**
  - change an expected value to the observed one
  - weaken, loosen, remove or skip an assertion, delete or skip a case, or `@Disabled`
  - suppress a rule (`@SuppressWarnings("PMD…")`, `// NOPMD`, `CHECKSTYLE:OFF`, ArchUnit freezing,
    ignore lists or exclusions)
  - edit `build/pmd`, `build/checkstyle` or the ArchUnit rules (`testing/test-rules`, `*ArchitectureTest`)
  - change production code (`src/main`) just to turn a test green
  - silence logs (raise log levels or remove appenders in `logback-test.xml`, catch and swallow
    exceptions in the harness or tests)
- **Green isn't the end of the check: read the logs too.** For backend runs, pass
  `-Dmaven.test.redirectTestOutputToFile=true` and run
  `java .claude/skills/it-gen/scripts/LogScan.java services/<svc>/target/failsafe-reports services/<svc>/target/container-logs`.
  Container logs (Postgres, Kafka, Redis) are written by test-support's `ContainerLogs` to
  `target/container-logs/<name>.log`. Report every ERROR, WARN or exception that no test deliberately
  provokes, the same way as a failure.

## HTTP 500 is never expected
A 500 means a bug: no code may produce one on purpose and no test may expect one. Outages are 503,
bad upstream answers 502; every outbound client uses `common.http.HttpClients.withTimeouts(...)` +
`common.http.Downstream`. A 500 seen at runtime (LogScan `unhandledHttp500: true`) is a product defect:
report it, don't assert it. Status mapping and enforcement: conventions.md, "HTTP 500 is never expected".

## Integration-test workflow
`/it-gen` generates, reviews and scores per-service ITs for **only the changed** services/endpoints
(agents `it-sme`, `it-writer`, `it-reviewer`; see `.claude/skills/it-gen/`). Target layout and rules
are in `.claude/skills/it-gen/conventions.md`. ITs live in `<service>/.../it/` (one class per
endpoint/listener, REST-Assured over RANDOM_PORT) with service-specific plumbing in `it/harness/`
(shared plumbing in `testing/test-support`). Its commands are in `.claude/skills/it-gen/SKILL.md`, "Running".

## Local environment notes
- Maven needs `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`.
- Docker runs in colima: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`,
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, `TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1`.
