# Testing Cookbook

Recipes for each kind of test in this repo. The rules every test follows (linear setup → action →
assert, exact values, one behaviour per test, parameterize, no magic values, DTOs only, Awaitility,
never `Thread.sleep`) and which tool enforces each are owned by
[`.claude/skills/it-gen/conventions.md`](../.claude/skills/it-gen/conventions.md) ("Test-method rules",
"Enforced by"). This cookbook shows how to apply them.

---

## 1. Per-service integration test (Testcontainers + WireMock)

Boot one service against real infrastructure; mock **only communication with other systems**
(other services' HTTP APIs, the exchange). Nothing internal is mocked: no Mockito / `@MockitoBean`
(conventions.md rule 8).

Layout: one class per endpoint or Kafka listener, service-specific plumbing in `it/harness/`, shared
plumbing in `testing/test-support` (full layout: conventions.md, "Layout"):

```
services/<svc>/src/test/java/com/example/demo/<pkg>/it/
  PlaceOrderIT.java  CancelOrderIT.java  OrderHistoryIT.java  OrderOutcomeConsumerIT.java …
  harness/<Svc>ITBase.java   # extends ServiceITBase: this service's containers + WireMock, properties, default stubs
  harness/stubs/*Stubs.java  # one fixture per external dependency, behaviour-named, provider-shaped DTOs
```

- `ServiceITBase` (test-support) carries `@SpringBootTest(webEnvironment = RANDOM_PORT)`; drive the API
  over real HTTP with **REST-Assured** (`api()` / `api(token)`).
- Real infra (Postgres / Confluent cp-kafka / Redis) from `TestContainers.postgres()/kafka()/redis()` as
  **singleton** containers started in a static block, so all `*IT` classes of a service share them and
  the cached Spring context. Wire them with `@DynamicPropertySource` (`TestContainers.datasource/kafka/redis`).
- External HTTP is a `WireMockServer`; the exchange feed is a WireMock **WS** message stub. Reset
  in `@BeforeEach`, assert no unmatched requests in `@AfterEach`.
- Kafka through `KafkaProbe` (acked sends, group-less snapshot reads; the polling loop lives there).
- The `TestContainers` factories attach `ContainerLogs`, which writes each container's output to
  `target/container-logs/<name>.log`; LogScan checks those files next to the service log.
- Downstream faults: the service answers **503** when a dependency is unreachable or slower than its
  read timeout, and **502** for an error status or an unreadable body. **Never expect a 500**
  (conventions.md, "HTTP 500 is never expected").
- Timeouts, deterministically:
  - In the base class's `@DynamicPropertySource`, set the dependency's read timeout short, e.g.
    `registry.add("exchange.read-timeout", () -> "1s")`.
  - Stub a fixed delay well beyond it (≥ 3×, e.g. `withFixedDelay(3000)`).
  - Assert 503 `<Dependency> unavailable`.
  - Never test near the boundary (a delay ≈ the timeout is a race).
- Time via the `@Primary` `MutableClock` (`@Import(MutableClockConfig.class)`; market-hours cases use
  exact boundary instants).
- Send a JWT from `TestJwt`: `api(token(TestUsers.ALICE))`; cover missing, malformed,
  wrong-signature (`forged`) and expired (`expired`) tokens → **401**.

Named `*IT` → run by **Failsafe** in `verify` (not Surefire/`test`). The fast `*ArchitectureTest`
(ArchUnit) checks stay `*Test` (Surefire). Failsafe runs with JaCoCo: the IT coverage report is
`services/<svc>/target/site/jacoco-it/index.html`.

New or changed endpoints: run **`/it-gen`** in Claude Code. It generates, reviews and scores the ITs
for only the changed services/endpoints (`.claude/skills/it-gen/`).

## 2. WireMock WebSocket mock (the exchange feed)

```java
configureFor("localhost", wireMock.port());
messageStubFor(message().withName("feed").withBody(equalTo(SUBSCRIBE_AAPL))
    .willTriggerActions(sendMessage(Json.write(AAPL_TICK)).onOriginatingChannel()));
```

Drive it with a raw `org.java-websocket` client. **Fault injection** (malformed frame, no-match →
silence) is in `WireMockWebSocketSpikeTest`. REST and WS stubs must live on **separate** WireMock
servers (the WS upgrade filter on `/*` breaks plain REST).

## 3. Cross-service flagship (`integration-tests`)

Boots the services as **isolated subprocesses** from their `-boot` jars (shared classpath would mix
auto-configs / config files). Shared infra via Testcontainers (`testing/test-support`); exchange via
two WireMock servers. Mint one JWT and pass it on every REST call, the STOMP CONNECT, and the health
probe. See `OrderToPortfolioFlowIT` + `harness/Stack` (plumbing, like any `harness/` package).
Container logs go to `integration-tests/target/container-logs/` and each service process logs to
`target/it-<svc>.log` (both checked by LogScan). `Stack.destroy` waits for the processes to exit
(graceful shutdown, 30 s budget) before the containers stop. Otherwise the services lose Kafka/Redis
mid-shutdown.

## 4. API + WS e2e (facade service objects)

Black-box against a **running stack** (`docker compose up`); the test boots nothing.

- Extend `BaseApiTest` (it carries `@Tag("api")`, `RequireRunningBackend`, which skips the class when
  `harness/Reachability` finds the stack down, and the per-test HTTP log, `TestLogArtifacts`); shared
  trading constants and `position(symbol)` are in `BaseTest`.
- Log in first: `AuthService.login(username, password).as(LoginView.class).token()`, with the demo
  credentials from the e2e config (`auth.username` / `auth.password`).
- Call only **service objects** (`OrdersService`, `PortfolioService`, `MarketDataService`,
  `NotificationsWsService`); they speak `ApiRequest`/`ApiResponse` and log full request/response.
- Wrap each step in `Allure.step("…")`.
- Assert exactly, even live values: the order echoes the request, a fill references the order id, live
  prices are cross-checked against the price market-data serves (conventions.md rule 4 applies to e2e
  too). See `com.example.demo.e2e.api.TradingTest`.

Add a service object: a multi-functionality service gets `Base<Name>Service` (with its
`serviceUrl()`) extended by `<Functionality>Service`; single-functionality extends `BaseService`.
Methods are `static`, return `ApiResponse`, **no validation** (negative cases must reach the server).

## 5. UI e2e (Playwright + Page Objects)

`-Pui-e2e`, against the running stack.

- Extend `BaseUiTest` (it carries `@Tag("ui")`, `RequireRunningBackend` and `PlaywrightLifecycle`).
- Page Objects (`LoginPage`, `DashboardPage`) receive the Playwright `Page` in their constructors;
  Playwright locators/assertions stay **inside** the pages — tests assert on returned values.
- `PlaywrightLifecycle` manages the browser and attaches screenshot/console/trace/video to Allure on
  failure.
- Reuse service objects for setup/cross-checks. See `com.example.demo.e2e.ui.TradingTest`.
- Browser mode (`ui.browser.mode = local | testcontainers`), headless, channel come from `Config`.

## 6. ArchUnit (structure)

The rules are libraries in `testing/test-rules`; each module runs them from a small class naming its
package (list and scopes: conventions.md, "Enforced by"):
- `<Svc>ArchitectureTest` (production): `ServiceArchitectureRules`: `*Controller` in `..web..`,
  `*Repository` interfaces in `..domain..`, no field injection, no standard streams, no
  `java.util.Date`, never produce a 500; plus service-specific extras.
- `<Module>TestArchitectureTest` (tests): `TestAssertionRules` (exact values, DTOs only, never sleep)
  and, in the services, `IntegrationTestRules` (mock only external communication, `*IT` in `it`).
- e2e framework (`E2eArchitectureTest`): `TestAssertionRules` plus REST-Assured confined to
  `BaseService`, STOMP to the `ws` package, Playwright to ui/pages/harness/arch, no bean-validation,
  `ApiRequest`/`ApiResponse` are records, service objects extend `BaseService` with `static` methods,
  Page Objects use explicit `Page` constructors.

## 7. PMD / Checkstyle / FE gates

- PMD (`build/pmd/`) and Checkstyle (`build/checkstyle/checkstyle.xml`) back the rules; which ruleset
  covers which rule, and in which modules, is conventions.md's "Enforced by" table. `harness/`
  packages and `src/main` are exempt from the test rules.
- How to satisfy the two that need the most care:
  - **No magic values** (rule 6): a semantic constant (`ORDER_QUANTITY`, not `TEN`), a local
    variable, or an annotation data table (`@CsvSource`). DTO factories shared across a service's ITs
    go into `harness/`.
  - **Exact values** (rule 4): `isEqualTo`, `containsExactly[InAnyOrder]`, `isEqualByComparingTo`,
    `isEmpty`, `equalToJson`, and make the data deterministic the ways rule 4 lists.
- **DTOs only** (rule 7): send DTOs/records and assert on deserialized objects; a hand-written
  payload only in a `@RawJsonPayload(reason = "…")` method (`com.example.demo.testing.RawJsonPayload`).
- Frontend: `npm run lint` (Rules of Hooks + boundaries) and `npm run depcruise` (no cycles,
  data layer must not import UI).

## Commands

```bash
mvn verify                              # everything except the e2e (which need a running stack)
mvn -pl services/order-service verify   # one service's integration test (*IT, Failsafe)
docker compose up -d && mvn -pl e2e-tests verify -Papi-e2e   # API/WS e2e
docker compose up -d && mvn -pl e2e-tests verify -Pui-e2e    # UI e2e
mvn -pl e2e-tests allure:report         # Allure HTML report
```
`/it-gen`'s commands (fast pre-check, LogScan) are in `.claude/skills/it-gen/SKILL.md`, "Running".
