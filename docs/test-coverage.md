# Test Coverage Map

This repository is a **testing showcase**. The application exists to support the tests, not the
other way around.

Read the repository with this split in mind:

- **Demo-grade application code**
  - backend services
  - frontend dashboard
  - support modules like `wiremock-ws-spike`
- **Production-grade test code**
  - unit tests
  - integration tests
  - component tests
  - API/WebSocket e2e tests
  - UI e2e tests
  - structural test-quality gates

## Coverage By Module

### `common`

What is covered:

- shared value types and helpers
- JWT issuance/validation helpers
- shared security configuration support

Primary test styles:

- unit tests
- architecture/style gates through the reactor build

### `services/auth-service`

What is covered:

- registration and login flows
- username/email/password validation
- duplicate-user handling
- JWT issuance
- auth error responses

Primary test styles:

- service-local unit tests
- service integration tests against Postgres via Testcontainers
- API e2e coverage through `e2e-tests`
- UI e2e coverage for login/registration through `e2e-tests`

### `services/order-service`

What is covered:

- order placement and cancellation
- market-hours validation
- funds and holdings guards
- pre-trade risk check (risk-service stubbed to allow by default; refusal 422 / outage 503 cases pending `/it-gen`)
- persistence of orders and lifecycle states
- outgoing exchange calls
- reaction to fills and outcomes

Primary test styles:

- service-local unit tests
- service integration tests with real DB and Kafka; exchange, portfolio, market-data and risk mocked by WireMock
- cross-service integration through `integration-tests`
- API e2e coverage for trading/security/funds flows
- UI e2e coverage through order-ticket and dashboard flows

### `services/market-data-service`

What is covered:

- demo price feed
- Redis-backed latest price behavior
- matching logic
- fill publication
- raw WebSocket/exchange-facing behavior where applicable

Primary test styles:

- unit tests for matcher/feed logic
- integration tests with Redis/Testcontainers
- cross-service integration through `integration-tests`
- API/WebSocket e2e coverage for live prices and fills

### `services/portfolio-service`

What is covered:

- position building and closing
- average price / unrealized P&L
- cash balance updates
- persisted recent fills
- portfolio/funds HTTP endpoints

Primary test styles:

- service integration tests with DB inspection through Testcontainers
- cross-service integration through `integration-tests`
- API e2e coverage for funds/portfolio flows
- UI e2e coverage for positions, fills, and balance behavior

### `services/notification-service`

What is covered:

- STOMP-over-SockJS gateway behavior
- authenticated WS connect
- user-scoped fill delivery
- public price topic delivery
- triggered price alerts pushed to the owner (`alerts.triggered` → `/user/queue/alerts`; IT pending `/it-gen`)

Primary test styles:

- unit/integration coverage around message fan-out
- cross-service integration through `integration-tests`
- API/WebSocket e2e coverage through live subscriptions
- UI e2e coverage through live dashboard/fills behavior

### `services/alert-service`, `services/watchlist-service`, `services/risk-service`

New services (price alerts; watchlists; per-user risk limits + pre-trade check). Product code and
the structural `*ArchitectureTest` exist; **integration tests are pending** the `/it-gen` run, which
generates them with the same harness layout as the other services. Until then they are verified by
a boot smoke test against Postgres (Flyway + `ddl-auto: validate`, 401 without / 200 with a JWT).

### `integration-tests`

What is covered:

- full backend chain across multiple services:
  - order placement
  - matching/fill
  - portfolio update
  - notification push

Primary test style:

- cross-service integration with real service processes and real infrastructure

Purpose:

- this is the flagship backend integration layer
- it proves the app works across service boundaries without going through the browser

### `frontend/web-dashboard`

What is covered:

- auth/session persistence behavior
- funds panel behavior
- portfolio rendering
- fills rendering
- live-market-data hook behavior
- order-ticket form behavior
- dependency/layer boundaries

Primary test styles:

- unit tests with Vitest
- browser component tests with WebdriverIO
- linting and dependency-cruiser checks
- UI e2e coverage through `e2e-tests`

### `e2e-tests`

What is covered:

- black-box API behavior against the running stack
- WebSocket/STOMP behavior against the running stack
- browser-level UI behavior against the running stack
- multi-user isolation
- market-hours behavior
- session/security behavior
- Allure artifact collection

Primary test styles:

- API e2e
- WebSocket e2e
- Playwright UI e2e
- architecture tests for the e2e layer itself

### `wiremock-ws-spike`

What is covered:

- WireMock WebSocket beta usage
- fault injection / protocol experimentation

Primary test style:

- focused proof-of-concept tests

Purpose:

- this is not core application logic
- it exists to demonstrate and validate the WireMock WebSocket testing approach used by the repo

## Coverage By Test Layer

### 1. Unit Tests

Goal:

- verify logic in small, deterministic scope
- keep feedback fast

Examples:

- backend calculation/validation logic
- frontend hooks and component state changes

### 2. Per-Service Integration Tests

Goal:

- verify one service against real infrastructure and real Spring wiring

Examples:

- Flyway migrations
- JPA repositories
- Kafka consumers/producers
- REST controllers
- service-to-infra integration

Typical tooling:

- Maven Failsafe (+ JaCoCo: `services/<svc>/target/site/jacoco-it/`)
- Testcontainers (singleton Postgres / Kafka / Redis per service)
- WireMock for the external HTTP/WS dependencies only; nothing internal is mocked (ArchUnit-enforced)
- REST-Assured over the real HTTP port (RANDOM_PORT)

One class per endpoint or Kafka listener, under `<service>/src/test/java/.../it/`, with plumbing
in `it/harness/`:

| Service | Classes (executed cases) | Total |
|---|---|---|
| auth | `LoginIT` (5), `RegisterIT` (14), `UsersIT` (5) | 24 |
| order | `PlaceOrderIT` (40), `CancelOrderIT` (6), `OrderHistoryIT` (2), `OrderOutcomeConsumerIT` (4) | 52 |
| market-data | `PriceIT` (2), `ExchangeFeedIT` (1), `OrderMatchingIT` (16) | 19 |
| portfolio | `PortfolioIT` (1), `HoldingsIT` (2), `FillsIT` (2), `BalanceIT` (2), `DepositIT` (3), `WithdrawIT` (2), `FillsListenerIT` (9), `PriceTicksListenerIT` (8) | 29 |
| notification | `StompHandshakeIT` (6), `PriceTickPushIT` (3), `FillPushIT` (1) | 10 |
| alert, watchlist, risk | none yet (pending `/it-gen`) | 0 |

New or changed endpoints are covered with `/it-gen` in Claude Code (`.claude/skills/it-gen/`), which
only analyses the services/endpoints that changed.

### 3. Frontend Component Tests

Goal:

- verify browser-facing behavior of isolated UI components

Examples:

- order ticket form behavior
- funds panel updates
- frontend validation/rendering

Typical tooling:

- WebdriverIO component runner

### 4. Cross-Service Integration Tests

Goal:

- verify the backend chain across multiple services without introducing browser/e2e noise

Examples:

- order placed in order-service
- fill produced by market-data-service
- portfolio updated
- STOMP notification emitted

### 5. API/WebSocket E2E Tests

Goal:

- verify the running system as a black box through public APIs and WS endpoints

Examples:

- auth, funds, trading, portfolio, notifications
- JWT security and owner isolation
- market-hours scenarios

Typical tooling:

- REST-Assured-based service objects
- STOMP/SockJS test client
- Allure

### 6. UI E2E Tests

Goal:

- verify the running system through the real browser UI

Examples:

- login/registration
- funds and portfolio behavior
- order-ticket behavior
- multi-user isolation
- live fills and live prices

Typical tooling:

- Playwright (Java)
- page-object layer
- Allure artifacts:
  - screenshot
  - console
  - trace
  - video
  - consolidated test log on failure

### 7. Structural Quality Gates

Goal:

- enforce test discipline and architectural boundaries

What is enforced:

- architecture boundaries with ArchUnit
- no control-flow-heavy tests with PMD rules
- import/style hygiene with Checkstyle
- frontend linting with ESLint
- frontend dependency boundaries with dependency-cruiser
- no internal mocking in ITs (ArchUnit `<Svc>IntegrationTestArchitectureTest`); no weak / non-deterministic
  assertions in any backend test (ArchUnit `<Module>TestAssertionsArchitectureTest` in every service, common,
  integration-tests and wiremock-ws-spike)
- no HTTP 500 produced or expected (PMD `no-internal-server-error.xml`, ArchUnit `neverProduceInternalServerError`)
- TestLint (fast regex pre-check) and LogScan (service, test and container logs of IT runs) in the `/it-gen` workflow

## What This Repository Is Showing

The point of the repository is not “look at this demo trading app.”

The point is:

- how to layer tests across unit, integration, component, API, WS, and UI levels
- how to keep tests deterministic even with asynchronous/distributed behavior
- how to structure black-box e2e suites cleanly
- how to collect useful diagnostics when tests fail
- how to enforce test-code quality, not just application-code quality
