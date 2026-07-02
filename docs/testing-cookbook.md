# Testing Cookbook

Recipes for each kind of test in this repo. Conventions (enforced by PMD + ArchUnit): tests are
**linear** (setup → action → assert, no `if/for/while/switch/try`), assert **exact** values,
**one behaviour per test**, **parameterize** where possible, async via **Awaitility** (never
`Thread.sleep`).

---

## 1. Per-service integration test (Testcontainers + WireMock)

Boot one service against real infrastructure; mock only the external exchange.

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@Testcontainers`.
- Real infra as `@Container` (Postgres / Confluent cp-kafka / Redis). Wire it with
  `@DynamicPropertySource` (`spring.datasource.*`, `spring.kafka.bootstrap-servers`, `spring.data.redis.*`).
- The exchange is a `WireMockServer` (REST stub) and/or a WireMock **WS** message stub.
- Drive REST with **REST-Assured**; assert Kafka output with `KafkaTestUtils`.
- Send a JWT: `Jwt.issue("tester", SECRET, 300)` → `Authorization: Bearer …`; add a no-token → **401** test.

Named `*IT` → run by **Failsafe** in `verify` (not Surefire/`test`). Examples: `OrderServiceIT`,
`MarketDataServiceIT`, `PortfolioServiceIT`, `AuthServiceIT`, `NotificationServiceIT`. The fast
`*ArchitectureTest` (ArchUnit) checks stay `*Test` (Surefire).

## 2. WireMock WebSocket mock (the exchange feed)

```java
configureFor("localhost", wireMock.port());
messageStubFor(message().withName("feed").withBody(equalTo("subscribe:AAPL"))
    .willTriggerActions(sendMessage("{\"symbol\":\"AAPL\",\"price\":190.40}").onOriginatingChannel()));
```

Drive it with a raw `org.java-websocket` client. **Fault injection** (malformed frame, no-match →
silence) is in `WireMockWebSocketSpikeTest`. REST and WS stubs must live on **separate** WireMock
servers (the WS upgrade filter on `/*` breaks plain REST).

## 3. Cross-service flagship (`integration-tests`)

Boots the services as **isolated subprocesses** from their `-boot` jars (shared classpath would mix
auto-configs / config files). Shared infra via Testcontainers; exchange via two WireMock servers.
Mint one JWT and pass it on every REST call, the STOMP CONNECT, and the health probe. See
`OrderToPortfolioFlowIT` + `Stack`.

## 4. API + WS e2e (facade service objects)

Black-box against a **running stack** (`docker compose up`); the test boots nothing.

- Log in first: `token = AuthService.login("demo","password").as(LoginView.class).token()`.
- Call only **service objects** (`OrdersService`, `PortfolioService`, `MarketDataService`,
  `NotificationsWsService`); they speak `ApiRequest`/`ApiResponse` and log full request/response.
- Wrap each step in `Allure.step("…")`. `@ExtendWith(RequireRunningBackend.class)` skips if down.
- Assert deterministic facts exactly (the order echoes the request; a fill references the order id);
  live values (prices) only for presence. See `com.example.demo.e2e.api.TradingTest`.

Add a service object: a multi-functionality service gets `Base<Name>Service` (with its
`serviceUrl()`) extended by `<Functionality>Service`; single-functionality extends `BaseService`.
Methods are `static`, return `ApiResponse`, **no validation** (negative cases must reach the server).

## 5. UI e2e (Playwright + Page Objects)

`-Pui-e2e`, against the running stack.

- Page Objects (`LoginPage`, `DashboardPage`) receive the Playwright `Page` in their constructors;
  Playwright
  locators/assertions stay **inside** the pages — tests assert on returned values.
- `@ExtendWith(PlaywrightLifecycle.class)` manages the browser and attaches
  screenshot/console/trace/video to Allure on failure.
- Reuse service objects for setup/cross-checks. See `com.example.demo.e2e.ui.TradingTest`.
- Browser mode (`ui.browser.mode = local | testcontainers`), headless, channel come from `Config`.

## 6. ArchUnit (structure)

- Per service (`*ArchitectureTest`): `*Controller` in `..web..`, `*Repository` interfaces in
  `..domain..`, no field injection, no standard streams, no `java.util.Date`.
- e2e framework (`E2eArchitectureTest`): REST-Assured confined to `BaseService`, STOMP to the `ws`
  package, Playwright to ui/pages/harness/arch, no bean-validation, `ApiRequest`/`ApiResponse` are
  records, service objects extend `BaseService` with `static` methods, Page Objects use explicit
  `Page` constructors.

## 7. PMD / Checkstyle / FE gates

- PMD bans control statements in `*Test`/`*IT` sources (`build/pmd/test-conventions.xml`).
- Checkstyle: import hygiene (`build/checkstyle/checkstyle.xml`).
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
