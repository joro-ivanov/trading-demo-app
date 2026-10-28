# Trading Dashboard — Testing Showcase

## Test Coverage

[How the e2e framework works](#how-the-e2e-framework-works)

The project is organized around multiple test layers, not a single test style.

Coverage summary:

- **Fast unit tests**
  - backend domain/service logic
  - frontend hooks/components in Vitest
  - no real infrastructure
- **Per-service integration tests**
  - each backend service against real dependencies in Testcontainers
  - database migrations, repositories, message consumers/producers, HTTP endpoints
- **Frontend component tests**
  - browser-level component behavior via WebdriverIO component runs
- **Cross-service integration tests**
  - flagship end-to-end backend flows across multiple services started together
- **API/WebSocket e2e**
  - black-box tests against a running stack using the facade service-object framework
- **UI e2e**
  - Playwright browser tests against the running dashboard
- **Structural quality gates**
  - ArchUnit
  - PMD test conventions
  - Checkstyle
  - ESLint
  - dependency-cruiser

The detailed suite map is in [`docs/test-coverage.md`](docs/test-coverage.md).

### Run the e2e suites (against the running stack)

The e2e tests **never boot the app**. They assume the application is already running via
`docker compose up`.

Full e2e documentation lives in [e2e-tests/README.md](/e2e-tests/README.md).
That file covers:

- what the API, WebSocket, and UI suites verify
- browser modes and fixed-clock runs
- the Allure failure artifacts for API and UI tests
- common troubleshooting steps
- Allure report generation via Maven

```bash
mvn clean install -DskipTests
docker compose up --build -d
mvn -pl e2e-tests clean verify -Papi-e2e     # API + WS e2e (facade service objects, Allure-stepped)
mvn -pl e2e-tests clean verify -Pui-e2e      # Playwright UI e2e (needs a browser: see below)
mvn -pl e2e-tests -am clean verify -Pall-e2e   # run all e2e-tests in one command (local browser mode)
mvn -pl e2e-tests -am clean verify -Pall-e2e -Dui.browser.mode=testcontainers   # run all e2e-tests with Playwright in Docker
mvn -pl e2e-tests allure:report        # render target/allure-results
```

Use `clean` when rerunning tests so old Surefire/Failsafe/Allure results are removed first.
Use `mvn clean install -DskipTests` again when you changed code in the reactor and want all modules
rebuilt before rerunning the e2e suite. If you only want to rerun the e2e tests against an already
built checkout, the `mvn -pl e2e-tests ... clean verify ...` commands above are sufficient. `-am`
ensures Maven also builds required reactor dependencies such as `common`.

Allure note:

- the project uses the `allure-maven` plugin
- `mvn -pl e2e-tests allure:report` downloads and runs the required Allure tooling through Maven
- no separate manual Allure installation is required for normal report generation

UI e2e uses a local browser by default. Install Chromium once:

```bash
java -cp "$(mvn -pl e2e-tests dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.microsoft.playwright.CLI install chromium
```

Or avoid a host browser entirely:

```bash
mvn -pl e2e-tests -am clean verify -Pui-e2e -Dui.browser.mode=testcontainers
mvn -pl e2e-tests -am clean verify -Pall-e2e -Dui.browser.mode=testcontainers
```

To force market-hours-gated tests to behave as if they are running at a specific UTC instant,
start the stack and run the tests with the same `app.clock.fixed-instant` value. This is useful
when you want to exercise the regular-session path on demand without waiting for the real clock:

```bash
mvn clean install -DskipTests
APP_CLOCK_FIXED_INSTANT=2026-07-01T14:00:00Z docker compose up --build -d
mvn -pl e2e-tests -am clean verify -Pall-e2e \
  -Dui.browser.mode=testcontainers \
  -Dapp.clock.fixed-instant=2026-07-01T14:00:00Z
```

Use a UTC instant that falls inside `09:30–16:00 America/New_York` when you want the market to be
treated as open. Omitting `app.clock.fixed-instant` keeps the normal real-time behavior.

The `mvn clean install -DskipTests` step is required here because the current Java service images
copy the already-built `target/*-boot.jar` files. `docker compose up --build` rebuilds the images,
but it does not compile the Java services on its own.

Target URLs and `ui.*` settings come from
`e2e-tests/src/test/resources/application-local.properties` and can be overridden via `-D` or
environment variables.

### How the e2e framework works

The `e2e-tests` module contains both the tests and the shared framework they are allowed to use.
The framework is deliberately small and enforced with ArchUnit/PMD so the test code stays readable,
diagnosable, and transport-agnostic.

API requests:

- tests call service objects such as `AuthService`, `OrdersService`, `PortfolioService`,
  `FundsService`, and `MarketDataService`
- service methods build an `ApiRequest` and return an `ApiResponse`; they do not assert status codes
  or validate inputs, so negative cases still reach the server
- `BaseService` is the only e2e class that talks to REST-Assured; swapping the HTTP engine should be a
  framework change, not a test rewrite
- response bodies are deserialized with `response.as(Dto.class)` or `response.asList(Dto.class)`;
  tests assert typed DTOs, not JSON paths, maps, `JsonNode`, or response string fragments
- each request/response is written to the per-test Allure `Full test log` attachment

WebSocket e2e:

- tests subscribe through `NotificationsWsService`, which hides STOMP/SockJS setup in the framework
- received frames are read through `WsSession`, including typed helpers such as
  `nextFrameAs(Fill.class, timeout)`
- WebSocket activity is logged chronologically with the API traffic in the same Allure test log

Playwright UI e2e:

- UI tests use Page Objects (`LoginPage`, `DashboardPage`, `OrderTicketPage`, `RegistrationPage`);
  test bodies do not call Playwright directly
- Page Objects receive the active Playwright `Page` explicitly in their constructors, and ArchUnit
  enforces that constructor shape
- `PlaywrightLifecycle` owns browser startup/teardown, context switching, traces, videos,
  screenshots, and console capture
- browser mode is configurable: local Chromium by default, or a Playwright Docker browser with
  `-Dui.browser.mode=testcontainers`
- UI negative tests use Page Object-owned `RouteStub` helpers for deterministic browser-side request
  stubbing, aborts, and hung responses

General e2e test conventions:

- tests are linear: setup → action → assert; PMD bans `if`, loops, `switch`, ternaries, and `try` in
  test sources
- long-running async behavior uses Awaitility or Playwright web-first waits, not `Thread.sleep`
- `Allure.step(...)` names the user-visible action being performed
- tests that place real orders are gated by `@RunDuringMarketHours`; use the fixed-clock flow above
  for deterministic anytime runs

### Timeout configuration

Playwright and the e2e waits are configurable from
`e2e-tests/src/test/resources/application-local.properties`.

Default Playwright waits:

- `ui.playwright.default-timeout=10s`
- `ui.playwright.navigation-timeout=10s`

Other common e2e wait settings:

- `ui.stub.hung-response-delay`
- `ui.testcontainers.startup-timeout`
- `e2e.await.timeout`
- `e2e.await.short-timeout`
- `e2e.await.stability-timeout`
- `e2e.await.stability-poll-delay`
- `e2e.ws.frame-timeout`
- `e2e.ws.frame.short-timeout`

Example overrides for a slower local machine:

```bash
mvn -pl e2e-tests -am clean verify -Pall-e2e \
  -Dui.browser.mode=testcontainers \
  -Dui.playwright.default-timeout=20s \
  -Dui.playwright.navigation-timeout=20s \
  -De2e.await.timeout=45s
```

### Frontend

Frontend-specific development notes live in
[`frontend/web-dashboard/README.md`](frontend/web-dashboard/README.md).

Recommended workflow: use the **devcontainer** in
[`frontend/web-dashboard/.devcontainer`](frontend/web-dashboard/.devcontainer) so the frontend
toolchain stays inside the container instead of on the host.

Typical flow:

1. Open the repo in VS Code.
2. Run `Dev Containers: Reopen in Container`.
3. Use the frontend devcontainer configuration.
4. After the container starts, work from `frontend/web-dashboard`.

The devcontainer currently:

- uses the `mcr.microsoft.com/devcontainers/typescript-node:24` image (Node 24 everywhere: `.nvmrc`,
  `package.json` `engines`, the devcontainer and the production `Dockerfile`)
- installs Mozilla's official Firefox + geckodriver for the WDIO component tests (`FIREFOX_BIN` /
  `GECKODRIVER_PATH`; amd64 and arm64), then runs `npm install`
- forwards port `5173` for the Vite dev server

Common frontend commands:

```bash
cd frontend/web-dashboard
npm run build        # tsc + Vite
npm run lint         # ESLint (Rules of Hooks + layer boundaries)
npm run test:unit    # Vitest
npm run test:component   # WDIO component tests
npm run depcruise    # dependency-cruiser boundary check
```

Or run all of them in a throwaway container, with nothing installed on the host (details in
[frontend/web-dashboard/README.md](frontend/web-dashboard/README.md)):

```bash
cd frontend/web-dashboard
docker build -f Dockerfile.test -t web-dashboard-tests . && docker run --rm web-dashboard-tests
```

Frontend rule:

- do not run the frontend toolchain on the host
- run frontend build, lint, Vitest, and WDIO component commands only inside the devcontainer
- `npm run test:unit` uses Vitest's forked pool by default because the default worker pool has shown
  shutdown hangs in this repo after successful test completion

Rebuilding the frontend locally:

- `npm run build` rebuilds the Vite production bundle in `frontend/web-dashboard/dist`
- if the app is being served through `docker compose`, rebuild/restart that stack as well so
  `http://localhost:8088` picks up the new frontend:

```bash
docker compose up --build -d
```

A reference project whose **primary product is its test suite**.

The backend services and frontend exist to give the tests realistic seams:

- synchronous REST calls
- asynchronous Kafka flows
- persisted state in Postgres
- cached/live data in Redis
- STOMP-over-SockJS browser notifications
- a browser UI with trading, funds, session, and multi-user behavior

This repository should be read with the following in mind:

- **Application code** in the backend services and frontend is intentionally demo-grade. It is here
  to create useful testing scenarios, not to model a production trading platform.

Full design rationale: [`docs/DESIGN.md`](docs/DESIGN.md). Per-test-type recipes:
[`docs/testing-cookbook.md`](docs/testing-cookbook.md). Full suite inventory and coverage map:
[`docs/test-coverage.md`](docs/test-coverage.md).

## Stack

Java 25 · Spring Boot 4 · Maven · Apache Kafka · PostgreSQL + Flyway · Redis · React 19 + Vite +
TypeScript. All deps latest-stable except WireMock (pinned to the `4.0.0-beta` WS line).

## Prerequisites

- Java 25
- Maven 3.9+
- Docker + Docker Compose
- For UI e2e in local-browser mode: Chromium installed via Playwright

Most of the test stack relies on Docker:

- `mvn verify` uses Testcontainers for per-service and cross-service integration tests
- `docker compose up` runs the full local app for manual testing and e2e
- UI e2e can avoid a host browser with `-Dui.browser.mode=testcontainers`

## Architecture

```
  React dashboard ──REST──► order / market-data / portfolio        ◄─JWT (auth-service issues)
       │  ▲                         │   │        ▲
       │  └────STOMP/WS─── notification│   │  Kafka (orders.submitted, price.ticks, fills)
       │                              ▼   ▼
       └───────────────────────► (Postgres ×2, Kafka, Redis) ◄── WireMock exchange (REST + raw WS)
```

| Module | What it is |
|---|---|
| `common` | Shared `Money` value type + `Jwt` issuer + `ApiSecurityConfig` (JWT resource server) |
| `services/auth-service` | Issues HS256 JWTs for demo users; serves the user list |
| `services/order-service` | Place/cancel orders → Postgres, route to exchange (REST), publish `orders.submitted` |
| `services/market-data-service` | Consume exchange WS feed → cache (Redis) + `price.ticks`; match orders → `fills` |
| `services/portfolio-service` | Build positions/avg-price + P&L (`Money`) from `fills` + `price.ticks` |
| `services/notification-service` | STOMP-over-SockJS gateway; fan out ticks/fills/triggered alerts (JWT-authenticated CONNECT) |
| `services/alert-service` | Price alerts (REST CRUD); fires on `price.ticks`, publishes `alerts.triggered` → pushed over WS to `/user/queue/alerts` |
| `services/watchlist-service` | Per-user watchlists enriched with batch prices from market-data |
| `services/risk-service` | Per-user risk limits + the pre-trade `POST /risk/check` order-service calls (fails closed) |
| `frontend/web-dashboard` | React 19 dashboard: live ticker, order ticket, portfolio, fills |
| `integration-tests` | Cross-service flagship (boots services as subprocesses) |
| `e2e-tests` | The e2e **framework** + API/WS e2e + Playwright UI e2e |
| `wiremock-ws-spike` | WireMock WS-beta proof + fault-injection demo |

Module-specific notes:

- [`common/README.md`](common/README.md)
- [`services/auth-service/README.md`](services/auth-service/README.md)
- [`services/order-service/README.md`](services/order-service/README.md)
- [`services/market-data-service/README.md`](services/market-data-service/README.md)
- [`services/portfolio-service/README.md`](services/portfolio-service/README.md)
- [`services/notification-service/README.md`](services/notification-service/README.md)
- [`services/alert-service/README.md`](services/alert-service/README.md)
- [`services/watchlist-service/README.md`](services/watchlist-service/README.md)
- [`services/risk-service/README.md`](services/risk-service/README.md)
- [`integration-tests/README.md`](integration-tests/README.md)
- [`e2e-tests/README.md`](e2e-tests/README.md)
- [`frontend/web-dashboard/README.md`](frontend/web-dashboard/README.md)
- [`wiremock-ws-spike/README.md`](wiremock-ws-spike/README.md)

## Quick start

### 1. Build the project

```bash
mvn clean install -DskipTests
```

Use this first on a clean checkout. It builds the full reactor and installs inter-module artifacts
such as `common`, which the `e2e-tests` module depends on.

### 2. Run the local app

```bash
docker compose up --build
```

The dashboard is available at `http://localhost:8088`.

Demo login:

- username: `demo`
- password: `password`

Stop the stack with:

```bash
docker compose down
```

If you changed the frontend and want the local app at `http://localhost:8088` to serve the updated
bundle, rebuild and restart the stack:

```bash
docker compose up --build -d
```

If the containers are already running and you want to force a fresh frontend image/container
recreation, use:

```bash
docker compose down
docker compose up --build -d
```

## Build & test

```bash
mvn clean verify           # all unit + per-service (Testcontainers) + cross-service + ArchUnit + PMD + Checkstyle
```

This excludes the `e2e-tests` tagged `api` / `ui` suites because those run against an already-running
stack.

## Test layers

1. **Per-service integration** (`*IT`, Failsafe, `mvn verify`): each service against real Postgres,
   Kafka and Redis in Testcontainers, with only its external dependencies (other services, the
   exchange REST + WS feed) mocked by WireMock. Nothing internal is mocked. One class per endpoint or
   listener under `<service>/it/`, plumbing in `it/harness/`. Container logs go to
   `target/container-logs/`. (`mvn test` runs only the fast unit and `*ArchitectureTest` ArchUnit
   checks via Surefire.) New and changed endpoints get their ITs from the `/it-gen` Claude Code
   workflow.
2. **Cross-service flagship** (`integration-tests`) — boots order + market-data + portfolio +
   notification as subprocesses and asserts the whole `order → fill → portfolio → STOMP push` chain.
3. **API + WS e2e** (`e2e-tests`, `-Papi-e2e`) — black-box via the facade service objects against a
   running stack, typed with `ApiRequest`/`ApiResponse`/DTOs, narrated with `Allure.step`.
4. **UI e2e** (`-Pui-e2e`) — Playwright Page Objects with explicit `Page` constructors drive the real
   dashboard; failure artifacts (screenshot/console/trace/video) attach to Allure.

For the detailed e2e execution model and reporting behavior, see
[e2e-tests/README.md](/e2e-tests/README.md).

## Quality gates

Four static checks guard the Java code. Each looks at the code at a different level, so each
catches what the others cannot:

| Check | Looks at | Catches | Runs in |
|---|---|---|---|
| **Checkstyle** | source form | star / unused / redundant imports, one top-level class per file | `mvn verify` (fails the build) |
| **PMD** | syntax tree | logic in tests: control statements everywhere; also `&&` `\|\|` `!` `==` `!=` and `forEach`/`filter`/`ifPresent` callbacks in the service modules; magic values (unnamed strings/numbers) in every test; producing or expecting HTTP 500 in the backend | `mvn verify` (fails the build) |
| **ArchUnit** | compiled classes and calls | layering, no field injection or `java.util.Date`; in ITs no Mockito, `*IT` in the `it` package, no weak assertions | `mvn test` (unit tests, fails the build) |
| **TestLint** | source text (regex) | a fast approximation of the PMD and ArchUnit test rules, incl. magic values | `/it-gen` gate and on demand |

- **Checkstyle** (`build/checkstyle/checkstyle.xml`) is a style checker. Here it only enforces import
  hygiene and one top-level class per file: cheap consistency that catches leftovers such as an
  unused import after a refactor.
- **PMD** parses source into a syntax tree and matches XPath rules against it.
  `build/pmd/test-conventions.xml` (all modules) bans `if/else`, `for`, `while`, `switch`, ternary and
  `try` in tests. `build/pmd/it-conventions.xml` (backend services) adds boolean and comparison
  operators and callback loops. Production code and `harness/` helper packages are exempt. The goal is
  linear tests (setup → action → assert): one scenario per test, and `@ParameterizedTest` wherever
  cases differ only in data.
  `build/pmd/no-magic-values.xml` (every module, e2e included) bans **magic values** in tests: every
  string and number a test uses must be named. That means a constant, a local variable, or an
  annotation data table such as `@CsvSource`. `0`, `1`, `""` and description prose (`Allure.step`,
  assertion descriptions, `AssertionError` messages) are allowed inline. ArchUnit can't enforce this,
  because the compiler inlines constants into the bytecode.
- **ArchUnit** loads the compiled bytecode and checks dependencies and method calls as JUnit tests.
  It sees what source parsing can't: which class owns a called method, annotations on fields, and
  calls inside lambdas.
  - `<Svc>ArchitectureTest` covers production layering.
  - `<Svc>IntegrationTestArchitectureTest` forbids internal mocking in ITs (only external
    communication is stubbed, with WireMock), keeps `*IT` in the `it` package and forbids expecting a 500.
  - `<Module>TestAssertionsArchitectureTest`, generated from one template into every backend module
    (the services, common, integration-tests and wiremock-ws-spike), forbids weak or non-deterministic
    assertions in *all* their tests: unit, ArchUnit and ITs. The banned calls are AssertJ
    `contains`/`isNotNull`/`isGreaterThan`…, Hamcrest `containsString`… and WireMock `containing`….
    e2e is excluded because it asserts live values on a running stack.
  - The e2e module has its own framework invariants: engine isolation, records, static service
    methods, explicit `Page` constructors.
- **TestLint** (`.claude/skills/it-gen/scripts/TestLint.java`) is a dependency-free, single-file Java
  script: `java TestLint.java <path>`. It returns `file:line` findings in under a second, with no Maven
  or Docker. The `/it-gen` workflow runs it after every writer round. PMD and ArchUnit remain the
  authority; TestLint is only fast feedback.
- **dependency-cruiser + ESLint** cover the frontend: layer boundaries and the Rules of Hooks.

None of these can tell whether an expected value is *correct*. That needs judgment, which is why
`/it-gen` also runs an independent reviewer agent that checks expectations against the production
code.

### Log analysis: a green run can still hide an error

A test can pass while the service logs an exception it swallowed, a rolled-back transaction or a
Kafka deserialization failure, or while Postgres logs a constraint violation. **LogScan**
(`.claude/skills/it-gen/scripts/LogScan.java`, another dependency-free single-file Java script) reads
three kinds of log from an IT run:
- **Service and test logs:** Failsafe writes each IT class's output to its own file when you add
  `-Dmaven.test.redirectTestOutputToFile=true` (`target/failsafe-reports/<Class>-output.txt`).
- **Container logs:** every Testcontainers container (Postgres, Kafka, Redis) is started with
  Testcontainers' `Slf4jLogConsumer` on a `container.<name>` logger. `logback-test.xml` writes each one
  verbatim to `target/container-logs/<name>.log`, separate from the service log.

```bash
mvn -pl services/order-service verify -Dmaven.test.redirectTestOutputToFile=true
java .claude/skills/it-gen/scripts/LogScan.java services/order-service/target/failsafe-reports services/order-service/target/container-logs
# compare reruns: java LogScan.java run1=<dir> run2=<dir> run3=<dir>
```

It reports every ERROR, WARN and exception, with:
- its cause chain and the first `com.example.demo` stack frame
- duplicates grouped by root cause (ids, numbers and timestamps normalised)
- counts per run and per test class (or `container:<name>`)

Container logs are read in their native formats: Postgres `ERROR/FATAL/WARNING` (with
`DETAIL`/`STATEMENT`), Kafka log4j and Redis. A request that ended in an unhandled exception is flagged
`unhandledHttp500: true`.

A finding seen in only some reruns points to nondeterminism. LogScan filters nothing. In `/it-gen`
the reviewer agent classifies each finding: it is `expected` only when a test deliberately provokes
it, and everything else is `unexpected`. Unexpected findings go to the user just like failing tests,
and all findings appear in the final report.

### HTTP 500 is never expected

A 500 means an unforeseen bug in the service, so the backend never returns one on purpose and no test
expects one:

| Situation | Status |
|---|---|
| a dependency or the database is unreachable or timed out (an outage) | **503** |
| a dependency answered with an error status or an unreadable body | **502** |
| a unique-constraint or concurrent-write conflict | **409** |
| bad input | **400** |

Every outbound client is built with `common.http.HttpClients.withTimeouts(connect, read)` and
translates failures with `common.http.Downstream`. `common.security.ApiErrorHandler` is the safety
net. The timeouts make the 503 real: a dependency that hangs fails the call after its read timeout,
instead of hanging the request. Each dependency's timeouts are a validated `*Properties` record in
`application.yaml`, overridable by env var:

| Caller → dependency | Connect | Read | Env override |
|---|---|---|---|
| order → exchange | 2s | 5s (routing places the order) | `EXCHANGE_CONNECT_TIMEOUT` / `EXCHANGE_READ_TIMEOUT` |
| order → portfolio, market-data, risk | 2s | 3s | `PORTFOLIO_…`, `MARKET_DATA_…`, `RISK_…` |
| alert → market-data | 2s | 3s | `MARKET_DATA_CONNECT_TIMEOUT` / `MARKET_DATA_READ_TIMEOUT` |
| watchlist → market-data | 1s | 2s (degrades to `pricesAvailable: false`) | `MARKET_DATA_CONNECT_TIMEOUT` / `MARKET_DATA_READ_TIMEOUT` |
| risk → portfolio | 2s | 3s | `PORTFOLIO_CONNECT_TIMEOUT` / `PORTFOLIO_READ_TIMEOUT` |

The rule is enforced statically:
- PMD `build/pmd/no-internal-server-error.xml`, on production and test code: `statusCode(500)`,
  `INTERNAL_SERVER_ERROR`, `internalServerError()`, `@ResponseStatus(INTERNAL_SERVER_ERROR)`
- ArchUnit `neverProduceInternalServerError` (production bytecode) and
  `integrationTestsNeverExpectInternalServerError`
- TestLint

At runtime, LogScan's `unhandledHttp500` flags any request that still ended in a 500. That's a product
defect to fix, never a status to assert. Test stubs may still simulate a *dependency* answering 500:
that's the scenario, and the service must answer 502.

### When a check or test fails: report it, never hide it

A failing gate (Checkstyle, PMD, ArchUnit, TestLint) or functional test (unit, per-service IT,
cross-service, e2e) may be revealing a real defect. It is **never** made to pass by changing the test
or the rule. The only exception is a genuine defect in the test code itself, such as a compile error,
an unused import, or a stub missing for a new dependency. It may be fixed only when the fix doesn't
change what the test verifies, and the fix is reported.

Everything else goes to a person, with:
- what the test verifies and why (the code line or requirement it is based on)
- exactly what fails (expected vs actual, or the rule and message)
- an analysis of the likely cause: product defect, ambiguous behaviour, wrong test assumption, or
  environment
- the options

These require an explicit human decision:
- changing an expected value to the observed behaviour
- weakening or removing an assertion, or deleting a case
- `@Disabled`
- rule suppressions (`@SuppressWarnings("PMD…")`, `// NOPMD`, `CHECKSTYLE:OFF`, ArchUnit freezing)
- edits to the gate configuration

The Claude Code setup enforces this in `CLAUDE.md` and the `/it-gen` agents. Its writer reports such
failures instead of fixing them, the orchestrator asks the user, and its reviewer scores any hidden
failure 0.

Security: all services validate a shared-secret HS256 JWT (issued by `auth-service`); the
notification WebSocket validates the token on the STOMP CONNECT frame.

## License

This project is licensed under the GNU AGPLv3. See the LICENSE file for details.

If you modify and deploy this software as a service, you must make the source code available.