# e2e-tests

The `e2e-tests` module is the black-box test layer for the running application stack.

It has two roles:

1. It contains the shared e2e framework in `src/main/java`.
2. It contains the API/WebSocket and UI test suites in `src/test/java`.

## Quality Boundary

- The application stack under test is **demo-grade application code**.
- The framework and tests in this module are intended to be **production-grade examples** of black-box
  end-to-end testing structure, determinism, diagnostics, and readability.

Unlike the per-service integration tests and the cross-service integration module, these tests do
not boot application services themselves. They assume the stack is already running, usually via
`docker compose up`.

## What This Module Covers

The suite is split into two tagged groups:

- `api`
  - HTTP API tests against the live stack
  - STOMP/WebSocket tests against the live notification gateway
  - exact assertions on status codes and response DTOs
  - security, validation, order lifecycle, funds, portfolio, and live-notification flows
- `ui`
  - Playwright browser tests against the real dashboard
  - page refresh/session behavior
  - trading ticket behavior
  - multi-user isolation
  - portfolio/fills lifecycle
  - UI negative cases and security checks

The tests are not unit-style checks. They verify that already-deployed components work together
through real network boundaries.

## Module Layout

```text
e2e-tests/
  src/main/java/com/example/demo/e2e/
    framework/    shared HTTP, WS, config, model, logging, page-object, and Allure support
  src/test/java/com/example/demo/e2e/
    api/          API and WebSocket e2e tests
    ui/           Playwright UI e2e tests
    harness/      JUnit extensions and runtime conditions
    arch/         ArchUnit rules for the e2e layer itself
```

Key design rules:

- service access goes through framework service objects, not ad hoc HTTP calls
- UI tests use page objects, not raw Playwright calls in test bodies
- API assertions deserialize into DTOs instead of asserting maps or string fragments
- Playwright, REST-Assured, and STOMP client usage are confined to the framework layer
- API test classes extend `BaseApiTest` (`@Tag("api")` + `RequireRunningBackend` + the per-test HTTP log, `TestLogArtifacts`); UI test
  classes extend `BaseUiTest` (`@Tag("ui")` + `RequireRunningBackend` + `PlaywrightLifecycle`). Shared trading
  constants and `position(symbol)` live in `BaseTest`; `harness/Reachability` is the backend probe
- JSON goes through `common.json.Json` (no e2e-specific facade)
- the same test rules as every other module (linear tests, exact values, no magic values, DTOs only,
  no sleeps), owned by `.claude/skills/it-gen/conventions.md`; `E2eArchitectureTest` includes the shared
  `TestAssertionRules` next to its own framework rules

## Prerequisites

- Java 25
- Maven 3.9+
- Docker + Docker Compose
- a running app stack
- for UI runs:
  - either local Playwright Chromium
  - or `-Dui.browser.mode=testcontainers`

The default target URLs come from:

- `e2e-tests/src/test/resources/application-local.properties`

Typical defaults:

- `auth.base.url=http://localhost:8082`
- `portfolio.base.url=http://localhost:8083`
- `ui.base.url=http://localhost:8088`
- `auth.username` / `auth.password`: the demo login (`demo` / `password`, matching the auth seed)
- `jwt.secret`: the services' shared HS256 secret, for tests that mint tokens (env `JWT_SECRET`)

## Recommended Workflow

### 1. Build the reactor

```bash
mvn clean install -DskipTests
```

This is the safe first step on a clean checkout and after Java code changes.

### 2. Start the local stack

```bash
docker compose up --build -d
```

### 3. Run the desired e2e suite

API + WebSocket only:

```bash
mvn -pl e2e-tests clean verify -Papi-e2e
```

UI only, local browser mode:

```bash
mvn -pl e2e-tests clean verify -Pui-e2e
```

UI only, Playwright in Docker:

```bash
mvn -pl e2e-tests -am clean verify -Pui-e2e -Dui.browser.mode=testcontainers
```

All e2e suites together:

```bash
mvn -pl e2e-tests -am clean verify -Pall-e2e
```

All e2e suites together with Playwright in Docker:

```bash
mvn -pl e2e-tests -am clean verify -Pall-e2e -Dui.browser.mode=testcontainers
```

Use `clean` so stale Failsafe and Allure results are removed before reruns.

## Fixed Market Clock

Many trading tests are gated by regular US market hours. To force the stack and the tests to agree
on a chosen time, pass the same fixed instant into both:

```bash
mvn clean install -DskipTests
APP_CLOCK_FIXED_INSTANT=2026-07-01T14:00:00Z docker compose up --build -d
mvn -pl e2e-tests -am clean verify -Pall-e2e \
  -Dui.browser.mode=testcontainers \
  -Dapp.clock.fixed-instant=2026-07-01T14:00:00Z
```

Important:

- the Docker images copy prebuilt jars
- `docker compose up --build` does not compile Java sources on its own
- if you changed backend code, rebuild with Maven before rebuilding the containers

## Browser Modes

### Local browser mode

Default:

```properties
ui.browser.mode=local
```

Install Chromium once:

```bash
java -cp "$(mvn -pl e2e-tests dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" \
  com.microsoft.playwright.CLI install chromium
```

### Testcontainers browser mode

Recommended when you do not want a host browser dependency:

```bash
mvn -pl e2e-tests -am clean verify -Pui-e2e -Dui.browser.mode=testcontainers
```

In this mode the browser runs in a Playwright Docker container. The test framework rewrites
host-local URLs like `http://localhost:8088` to a Docker-reachable host alias for the remote
browser session.

## Parallel Execution

JUnit is configured to run up to four test classes in parallel.

- class-level parallelism: enabled
- method-level parallelism: disabled
- fixed parallelism: `4`

That means:

- up to four test classes can run at once
- methods inside one class still run sequentially

## Timeouts

The suite uses configuration-backed timeouts. The default Playwright waits are:

- `ui.playwright.default-timeout=10s`
- `ui.playwright.navigation-timeout=10s`

Other important configurable waits:

- `ui.stub.hung-response-delay`
- `ui.testcontainers.startup-timeout`
- `e2e.await.timeout`
- `e2e.await.short-timeout`
- `e2e.await.stability-timeout`
- `e2e.await.stability-poll-delay`
- `e2e.ws.frame-timeout`
- `e2e.ws.frame.short-timeout`

Example:

```bash
mvn -pl e2e-tests -am clean verify -Pall-e2e \
  -Dui.browser.mode=testcontainers \
  -Dui.playwright.default-timeout=20s \
  -Dui.playwright.navigation-timeout=20s \
  -De2e.await.timeout=45s
```

## Allure Reporting

Generate the HTML report from the module:

```bash
mvn -pl e2e-tests allure:report
```

Important:

- report generation does not require a vendored Allure distribution
- this module already uses the `allure-maven` plugin, so `mvn -pl e2e-tests allure:report`
  downloads and runs the required Allure tooling through Maven
- no separate manual Allure installation is required for the normal Maven report flow

The raw results are written under:

- `e2e-tests/target/allure-results`

The generated HTML report is written under:

- `e2e-tests/target/site/allure-maven-plugin`

### What Allure Contains

For API and WebSocket tests:

- the JUnit test and step structure
- a single consolidated `Full test log` attachment per test
- request/response and WS activity in chronological order

For UI tests:

- the JUnit test and step structure
- screenshot on failure
- browser console attachment
- Playwright trace archive on failure
- Playwright video on failure

### Failure Artifacts For UI Tests

On a failing UI test, Allure is expected to include:

- `screenshot`
- `console`
- `trace.zip`
- `video.webm`

These artifacts are captured during teardown after the test fails and before the browser/container
resources are fully shut down.

The corresponding raw files are typically stored under:

- `e2e-tests/target/allure-results`
- `e2e-tests/target/playwright-traces`
- `e2e-tests/target/playwright-videos`

### What To Look At First In A Failure

For API failures:

1. the failing assertion in the test report
2. the `Full test log` attachment
3. the exact response body and status code

For UI failures:

1. screenshot
2. console attachment
3. `trace.zip`
4. `video.webm`

The screenshot usually answers what the page looked like at failure time. The trace and video are
useful when timing or interaction order matters.

## Test Coverage Areas

The current suite intentionally covers flows that are easy to regress in a realistic stack:

- authentication and registration
- malformed or missing JWTs
- funds deposit/withdraw validation
- order placement, rejection, pending, fill, and cancel paths
- live STOMP notifications for prices and fills
- portfolio updates after fills
- UI order ticket behavior, including negative cases
- persistence of recent fills across relogin
- session behavior across refresh, logout, and tab close
- multi-user data isolation

The suite also includes market-hours-aware scenarios. Those are skipped outside regular session
unless the fixed clock is used.

## Troubleshooting

### Tests are skipped because the market is closed

Either:

- run during real market hours
- or use the fixed-clock flow described above

### UI tests cannot reach the app in Testcontainers mode

Check:

- the stack is actually running
- `ui.base.url` is correct
- the run is using `-Dui.browser.mode=testcontainers`

### Allure shows stale failures

Use `clean` on reruns:

```bash
mvn -pl e2e-tests -am clean verify -Pall-e2e
```

### The stack does not reflect recent code changes

Rebuild jars before `docker compose up --build`:

```bash
mvn clean install -DskipTests
docker compose up --build -d
```

### A UI test is quiet for a long time

UI runs log explicit per-method start and finish lines. If a long-running class is in progress,
watch the Maven log for:

- `UI test started: ...`
- `UI test finished: ...`

## Commands Reference

```bash
mvn clean install -DskipTests
docker compose up --build -d

mvn -pl e2e-tests clean verify -Papi-e2e
mvn -pl e2e-tests clean verify -Pui-e2e
mvn -pl e2e-tests -am clean verify -Pui-e2e -Dui.browser.mode=testcontainers
mvn -pl e2e-tests -am clean verify -Pall-e2e
mvn -pl e2e-tests -am clean verify -Pall-e2e -Dui.browser.mode=testcontainers

mvn -pl e2e-tests allure:report
```
