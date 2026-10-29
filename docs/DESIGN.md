# Design Doc / PRD — Real-Time Trading Dashboard Testing Showcase

## 1. Purpose & Goals

A reference Java project whose **primary product is its test suite**. A small real-time
trading dashboard exists only to give the tests something realistic to exercise. The repo
is a teaching/showcase artifact demonstrating, end to end:

1. **Per-service integration tests** with **Testcontainers** — each service tested against
   its *real* backing infra (Postgres, Kafka, Redis) in throwaway containers.
2. **Multi-service integration tests** — several services booted together across real
   boundaries (REST + Kafka + WebSocket).
3. **API integration tests** against real datastores.
4. **WebSocket integration tests** with a real WS client asserting streamed frames.
5. **Mocking unstarted collaborators with WireMock** — including **WireMock's WebSocket
   mocking (latest beta)** to stand in for the upstream exchange's live price feed.
6. **API e2e tests** — a black-box suite driving the running app through a **facade /
   service-object framework** (§8). A first-class deliverable.
7. **UI e2e tests with Playwright for Java** — Page Object pattern, multi-context flows,
   reusing the same service objects to arrange/clean up data.
8. **Allure reporting** with rich failure artifacts (full logs for API; logs + screenshot +
   video + console + trace for UI).
9. **ArchUnit + PMD/Checkstyle** guarding the service code and the e2e framework (plus a
   TS-native equivalent for the frontend — §13).

### Non-goals
- Not a production trading system: no real money/exchange, demo-grade auth, no scaling.
- Not a latency/perf showcase. Correctness and test ergonomics only.
- Matching/pricing logic is intentionally simple; we still use fixed-point money (§10).

### Success criteria
- `mvn test` (fast unit + ArchUnit), `mvn verify` (per-service `*IT` + cross-service flagship via
  Failsafe), and `mvn verify -Papi-e2e` / `-Pui-e2e` (against a running stack) each run their
  category green and independently.
- Every test category has at least one clear, well-commented example.
- The Allure report makes a failing test trivially investigable.
- The repo doubles as a cookbook (`docs/testing-cookbook.md`).

---

## 2. Domain Overview

A user logs into a dashboard showing: a **live ticker** of streaming prices, an **order
order ticket** (market/limit buy/sell), and a **portfolio panel** with positions, average price,
and live P&L that updates as prices move and orders fill.

An upstream **exchange** publishes a live WS price feed and accepts order routing over REST.
We never run a real exchange — it is **always WireMock** (REST routing + WS feed). It is the
canonical "service we don't start, so we mock it," and justifies WireMock's WS mocking.

---

## 3. Service Architecture

Five backend services + one frontend + the always-mocked exchange.

```
                         ┌────────────────────────────────────────────┐
                         │            web-frontend (React)             │
                         │   live ticker · order ticket · portfolio    │
                         └───────────────┬───────────────┬─────────────┘
                              REST/WS     │               │  WS (prices, fills)
                         ┌───────────────▼──┐         ┌───▼──────────────┐
                         │   order-service  │         │ notification-svc │
                         │   (REST)         │         │   (WS gateway)   │
                         │   Postgres       │         │   Redis pub/sub  │
                         └────────┬─────────┘         └───▲──────────────┘
                          Kafka:  │ orders.submitted          │ Kafka: fills, price.ticks
                                  │                           │
                  ┌───────────────▼────────────┐    ┌─────────┴───────────────┐
                  │     market-data-service    │    │    portfolio-service    │
                  │  consumes exchange WS feed │    │  positions & live P&L   │
                  │  produces price.ticks      │    │  Postgres               │
                  │  matches/fills orders      │    │  consumes fills+ticks   │
                  │  Redis (latest prices)     │    └─────────────────────────┘
                  │  Kafka (ticks, fills)      │
                  └───────────────▲────────────┘
                                  │ WS price feed + REST order routing
                  ┌───────────────┴────────────┐
                  │   exchange  (EXTERNAL)      │
                  │   *** ALWAYS WireMock ***   │
                  └────────────────────────────┘
```

| Service | Responsibility | Backing infra (Testcontainers) | Interfaces |
|---|---|---|---|
| **order-service** | Accept/validate/cancel orders; persist lifecycle; publish `orders.submitted` | Postgres | REST in; Kafka out |
| **market-data-service** | Subscribe to exchange WS feed; publish `price.ticks`; match resting orders → `fills` | Redis, Kafka | Exchange WS+REST in; Kafka in/out |
| **portfolio-service** | **Per-user** positions/avg-price; recompute P&L per tick; expose portfolio REST | Postgres, Kafka | Kafka in; REST out |
| **notification-service** | WS gateway to browser; fan out ticks + fills | Redis pub/sub | Kafka in; WS out |
| **auth-service** | Issue demo JWT; manage demo users | Postgres | REST |
| **exchange** | Upstream feed + routing — **never started, always WireMock** | — | WS + REST (mocked) |

**Kafka topics:** `orders.submitted`, `price.ticks`, `fills`.

---

## 4. Technology Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | **Java 25** | records, pattern matching, virtual threads |
| Backend | **Spring Boot 4 / Spring Framework 7** | Web, WebSocket, Kafka, Data JPA |
| Build | **Maven**, multi-module reactor | profiles per test category (§11) |
| Messaging | Apache Kafka (**Confluent `cp-kafka`** in Testcontainers) | `spring-kafka` |
| App WebSocket | **STOMP over SockJS** | browser-facing notification WS (ours) |
| Exchange WebSocket | **raw WS JSON** | external upstream feed — **always WireMock-mocked** (§6) |
| Relational | PostgreSQL + Flyway | per-service schema |
| Cache / pub-sub | Redis | latest prices + WS fan-out |
| Frontend | React 19 + Vite + TypeScript | **devcontainer** for dev (§12) |
| Containers in tests | **Testcontainers** | postgres, cp-kafka, redis, + Playwright browser server |
| HTTP mocking | **WireMock** (JUnit 5) | latest **beta** for WS (only beta dep) |
| WS mocking | **WireMock WebSocket (beta)** | mocks raw exchange feed |
| Integration API assertions | REST-Assured | per-service/integration layer |
| e2e HTTP client | **REST-Assured**, isolated inside `BaseService` (swappable to JDK `HttpClient` by editing one class; ArchUnit-enforced) | §8.3, §13.1 |
| WS client (tests) | Spring **`WebSocketStompClient`** (app STOMP WS) · **`org.java-websocket`** (raw exchange-feed mock) | frame capture for assertions |
| UI e2e | **Playwright for Java** | Page Objects, trace/video/screenshot |
| Reporting | **Allure** (`io.qameta.allure`) | steps + failure artifacts |
| Arch/quality gates | **ArchUnit** (structure) + **PMD/Checkstyle** (control-flow) + **dependency-cruiser/eslint-boundaries** (FE) | §13 |
| Assertions / async | AssertJ + Awaitility | |

### 4.0 Spring Boot 4 modular auto-configuration (gotcha — learned in M2)
Spring Boot 4 split auto-configuration into per-technology modules. Putting the underlying
library on the classpath is **no longer enough** to activate its auto-config; you must add the
matching `spring-boot-<tech>` module. Discovered while building order-service:
- **Flyway** → add `org.springframework.boot:spring-boot-flyway` (flyway-core alone does nothing).
- **Kafka** → add `org.springframework.boot:spring-boot-kafka` (brings spring-kafka; gives `KafkaTemplate`).
- **RestClient** → add `org.springframework.boot:spring-boot-restclient` (for the `RestClient.Builder` bean).
Auto-config classes moved too (e.g. `org.springframework.boot.flyway.autoconfigure.*`). Also note:
**`TestRestTemplate` was removed** in Boot 4 — per-service/e2e HTTP assertions use REST-Assured
(or the new `RestTestClient` in spring-test 7). Each new service module must add the
`spring-boot-<tech>` modules it needs.

**Compiler `-parameters` is mandatory.** The reactor parent sets `<parameters>true</parameters>`
on maven-compiler-plugin (Spring Boot's own parent does this; ours must too). Without it,
`@PathVariable`/`@RequestParam` without explicit names and `@Value`/constructor binding fail at
runtime (`parameter name information not available via reflection`). Other Boot-4/TC-2 specifics:
Testcontainers renamed modules (`testcontainers-postgresql`/`-kafka`/`-junit-jupiter`) and added
new container packages (`org.testcontainers.kafka.ConfluentKafkaContainer`,
`org.testcontainers.postgresql.PostgreSQLContainer`); Surefire runs `*Test` (per-service), not
`*IT` (reserved for Failsafe / cross-service).

### 4.2 Cross-service integration gotchas (learned in M5)
Booting multiple services together surfaced several issues; the fixes are baked into the repo:
- **In-JVM multi-context fails on a shared classpath.** Every service sees every other service's
  auto-configuration (e.g. market-data/notification pick up JPA+datasource from order/portfolio
  and fail "no driver class"), and `application.yaml` + `db/migration/V1__*.sql` collide. Fix:
  launch each service as an **isolated subprocess** from its executable `-boot` jar (own
  classpath, own config). Migrations are still relocated to per-service dirs
  (`db/migration/<service>`) so the plain jars remain conflict-free if ever co-located.
- **No `spring-boot-starter-parent` ⇒ the Boot plugin needs explicit setup.** The plugin has no
  managed version and no `repackage` binding by default; the parent pluginManagement now sets the
  version + a `repackage` execution, and each service uses `<classifier>boot</classifier>` so the
  plain jar stays depend-able and the `-boot` jar is executable.
- **WireMock REST + WS on one server breaks REST.** Registering a WS message stub adds the WS
  upgrade filter on `/*`, and plain REST calls then fail with EOF. Use **two WireMock servers**
  (one REST, one WS).
- **RestClient defaults to JDK HttpClient (HTTP/2), which fails against WireMock's h2c with EOF.**
  Add **Apache HttpClient5** to services that call WireMock over REST so RestClient uses HTTP/1.1.
  (Per-service tests masked this because `rest-assured` dragged HttpClient5 onto the test classpath.)
- **Iterating the IT:** `mvn -N install` (parent pom) + `mvn -pl services/* install -DskipTests`
  once, then `mvn -pl integration-tests verify`. Full reactor: `mvn verify`.

### 4.1 Versions & risks (latest stable everywhere except WireMock)
- **All deps latest stable** at scaffold time — except **WireMock**, pinned to the latest
  **beta** (required for WS mocking). Versions captured centrally in the parent `pom.xml`
  `<dependencyManagement>` / BOM imports.
- **Spring Boot 4 GA** pin + starter alignment with `spring-kafka`.
- **WireMock WS beta** coordinates + DSL — highest risk; spike (§9).
- **Kafka Testcontainers** → **Confluent `cp-kafka`** (decided).
- **Playwright Java** → latest stable; the Testcontainers browser-server image tag is pinned
  to the **same** Playwright version to avoid client/server drift (§7.4).
- **Java 25 + Spring Boot 4** toolchain (`--release 25`, surefire/failsafe args).

---

## 5. Repository Layout

```
demo-app-java/
├── pom.xml                          # parent reactor: modules, BOMs, profiles, quality gates
├── docs/{DESIGN.md, testing-cookbook.md, test-coverage.md}
├── common/                          # shared Jwt, security config, ApiErrorHandler, Money, Clocks, MarketHours
├── testing/
│   ├── test-rules/                  # shared ArchUnit rule libraries + the only @RawJsonPayload
│   └── test-support/                # shared IT plumbing: ServiceITBase, TestContainers, KafkaProbe, TestJwt, …
├── services/
│   ├── pom.xml                      # parent of the services: shared deps, service PMD rulesets, JaCoCo, boot jar
│   ├── order-service/               # src/main + src/test (per-service TC + WireMock)
│   ├── market-data-service/
│   ├── portfolio-service/
│   ├── notification-service/
│   ├── auth-service/
│   ├── alert-service/
│   ├── watchlist-service/
│   └── risk-service/
├── frontend/web-dashboard/          # React + Vite + TS  (+ .devcontainer/)
├── integration-tests/               # cross-service suite (boots several services)
├── e2e-tests/                       # ★ SINGLE e2e module, three subpackages:
│   └── src/test/java/.../e2e/
│       ├── framework/               #   ApiRequest/ApiResponse, BaseService + service objects,
│       │                            #   WS service objects, Config singleton, Page-Object base,
│       │                            #   Allure hooks/extensions
│       ├── api/                     #   API e2e tests (use framework.services only)
│       └── ui/                      #   Playwright UI e2e tests (Page Objects + framework.services)
├── wiremock-ws-spike/               # WireMock WS DSL spike + fault injection
├── .devcontainer/                   # FE dev container (§12)
└── docker-compose.yml               # run the whole app (BE+FE) in Docker (§12)
```

> Per request, all e2e code lives in **one** `e2e-tests` module under a single `...e2e`
> package root with three subpackages: `framework`, `api`, `ui`. `ui` tests depend on
> `framework.services` to arrange/clean data; both `api` and `ui` tests may import **only**
> `framework.pages` (`*Page`) and `framework.services` (`*Service`) types (enforced, §13).

---

## 6. WebSocket Strategy

**Two protocols, by design** — they serve different roles, so they intentionally differ:

| Surface | Protocol | Why |
|---|---|---|
| **Exchange → market-data-service** (inbound, external) | **raw WS JSON** | A third-party exchange wouldn't speak STOMP; raw frames are what **WireMock's WS beta** mocks cleanly. This is the headline mock. |
| **notification-service → browser** (outbound, ours) | **STOMP over SockJS** | Spring messaging + rich FE client; natural subscribe/destination model for ticks & fills. |

Three test angles:
1. **Exchange feed (raw WS).** Always **WireMock WS**; mock pushes scripted price frames;
   assert market-data-service emits `price.ticks`. **The headline WireMock-WS demo.** Asserted
   in tests with a raw `org.java-websocket` client where the mock side is exercised directly.
2. **notification WS (STOMP/SockJS).** Tested via a Spring `WebSocketStompClient` (subscribe to
   a destination, capture frames) and via Playwright (UI sees updates).
3. **Order fill round-trip.** REST order → `orders.submitted` → matched vs mocked raw-WS price →
   `fills` → portfolio update → STOMP push to browser. Flagship multi-service test.

> Decision (was open Q1): **raw WS for the mocked exchange, STOMP/SockJS for our notification
> gateway.** This keeps the WireMock-WS showcase tractable while giving the app an idiomatic
> Spring WS stack. If a single uniform protocol is ever preferred, the exchange side is the one
> to keep raw (mockability wins).

---

## 7. Test Strategy

### 7.1 Categories & Maven profiles

| Profile / phase | Scope | Boots | Mocks | Example |
|---|---|---|---|---|
| `mvn verify` (Failsafe `*IT`) | per-service slice | one service + its containers | exchange (WireMock) | order persists to Postgres |
| `mvn verify` (failsafe) | cross-service | 2–4 services (in-JVM/subprocess) | exchange only | order → fill → portfolio → WS |
| `-Papi-e2e` (failsafe) | black-box API | **nothing — runs against a running stack** | none | order lifecycle via service objects |
| `-Pui-e2e` (failsafe) | end-to-end UI | **nothing — runs against a running stack** | none | Playwright places an order |

> **e2e principle:** the e2e tests (API + UI) **never boot the app**. They run against a
> deployed environment — locally a manually started `docker compose up` stack. Target URLs come
> from `Config` (`-D`/env overrides per environment; `application-local.properties` points at the
> compose ports). Both groups are **excluded from the default build** and run via
> `mvn -pl e2e-tests verify -Papi-e2e` / `-Pui-e2e` after the stack is up; if it is not reachable
> the suite **skips**. Only the cross-service *integration* test boots services (that is its
> nature). Because the env is live, e2e assert what is deterministic (the order echoes the
> request; a fill references the placed order id) and only that live values (prices) are *present*.

### 7.2 Representative cases (acceptance checklist)

**Per-service (Testcontainers + WireMock):**
- order-service: POST /orders persists to real Postgres; invalid → 400; routed via WireMock
  REST stub; asserts `orders.submitted` produced.
- market-data-service: connect to **WireMock raw WS** feed; mock pushes 5 frames; assert 5
  `price.ticks` produced and latest cached in real Redis.
- portfolio-service: given `fills` + `price.ticks`, avg-price & P&L persisted.
- notification-service: tick → real Redis/Kafka → **STOMP `WebSocketStompClient`** subscribed
  to the destination receives the frame.

**Multi-service (`integration-tests`):**
- **Flagship:** boot order + market-data + portfolio + notification + cp-kafka + Postgres +
  Redis + WireMock exchange (REST + raw WS). A STOMP client subscribes. POST buy order.
  WireMock raw WS pushes matching price. Assert `fills` produced, portfolio REST shows
  position, STOMP client gets a fill frame.
- **Mock-only variant:** boot only notification + market-data; mock order-service by
  producing `orders.submitted` directly.

**API e2e (`e2e-tests/api`):** full functional coverage through service objects — login,
place/cancel order, query history, read portfolio, subscribe prices (WS service) — each step
`Allure.step(...)`. See §8.

**WS integration:** ordered delivery, reconnect/resubscribe, interleaved fills.

**UI e2e (`e2e-tests/ui`, Playwright):** login → ticker updates → place order → portfolio
reflects fill; multi-context (two browsers observe the same market move). Data arranged via
service objects.

### 7.3 Test conventions (HARD rules, enforced by §13)
Owned by `.claude/skills/it-gen/conventions.md` ("Test-method rules", with the tool behind each rule in
"Enforced by"). In short: one behaviour per test; exact values (never ranges/inequalities); no control
statements in test code (linear setup → act → assert); `@ParameterizedTest` where inputs differ only in
data; no magic values; DTOs only; Awaitility with explicit timeouts, never `Thread.sleep`.

### 7.4 Running UI tests without host browsers
Property `ui.browser.mode = local | testcontainers`:
- `local` — Playwright engines on host (chromium/firefox/webkit); `ui.browser.headless`
  toggles headless; `ui.browser.channel` selects a **real** installed browser (`chrome`,
  `msedge`) instead of the bundled engine.
- `testcontainers` — start the Playwright server image in a container running
  `playwright launch-server`; tests `BrowserType.connect(wsEndpoint)` to it. No host browser
  install; video/trace pulled back from the container.

---

## 8. API e2e Framework (facade / service objects)

Black-box tests never touch HTTP directly — they call **static service methods** speaking in
`ApiRequest`/`ApiResponse`.

### 8.1 `ApiRequest` / `ApiResponse`

`ApiRequest` is an **immutable record with a builder** (records-with-builders is idiomatic):

```java
public record ApiRequest(
    String path,                       // e.g. "/orders/{id}"
    HttpMethod method,
    Object body,                       // serialized to JSON by BaseService
    Map<String, String> headers,
    Map<String, String> pathParams,    // fills {id}
    Map<String, String> queryParams,
    Map<String, String> cookies,
    String token                       // convenience -> Authorization: Bearer
) {
    public static Builder builder() { return new Builder(); }
    // Builder: fluent setters + addHeader/addQueryParam/addPathParam/addCookie;
    // build() wraps maps unmodifiable. NO validation in build() (see 8.2).
}
```

```java
public record ApiResponse(
    int statusCode,
    String body,                       // raw
    Map<String, String> headers,
    Map<String, String> cookies,
    long durationMs
) {
    public <T> T as(Class<T> type) { ... }            // single object
    public <T> List<T> asList(Class<T> type) { ... }  // JSON array -> List<T>
}
```

### 8.2 No defensive programming in the framework/services
Service objects and `BaseService` perform **no input validation, no null-checks, no
`Objects.requireNonNull`, no defensive throws**. They are pure dumb pipes: whatever the test
passes is sent verbatim. This is deliberate so **negative scenarios are testable** — a test
can send a missing token, malformed body, blank path param, or invalid enum and assert the
**real server's** 4xx/5xx response and message. Validation belongs to the application under
test, never to the test framework. (Enforced by ArchUnit, §13.)

### 8.3 `BaseService` — the dumb pipe

```java
public abstract class BaseService {
    protected static ApiResponse execute(ApiRequest request) {
        String fullUrl = Config.get().baseUrl() + resolvePath(request);   // path + pathParams + queryParams
        logRequest(fullUrl, request);     // FULL url + body + headers + params + cookies
        ApiResponse response = send(fullUrl, request);                     // REST-Assured lives ONLY here
        logResponse(response);             // FULL body + status code + headers + duration
        return response;                   // no status assertions here — tests decide
    }
    // The ONLY place REST-Assured (io.restassured..) is referenced in the whole codebase.
    // Swapping to JDK HttpClient = rewrite this one method; nothing else changes. (ArchUnit §13.1)
    private static ApiResponse send(String fullUrl, ApiRequest request) { /* REST-Assured */ }
}
```

`logRequest`/`logResponse` write to the per-test captured log (attached to Allure on failure,
§11) and add an Allure attachment for the request/response pair. **REST-Assured is confined to
`BaseService.send`** — the facade's whole point is engine-swappability, enforced by ArchUnit.

### 8.4 Service-object inheritance rules
- **Multiple functionalities** → `Base<Name>Service extends BaseService` (base path + shared
  headers), extended by one `<Functionality>Service` per functionality.
- **Single functionality** → `<Name>Service extends BaseService` directly.
- Methods are `static`, return `ApiResponse`. If a call needs **more than a few args**, it
  accepts a prebuilt `ApiRequest`; otherwise `token` + the few args and builds the request
  internally.

| Service | Functionalities | Classes |
|---|---|---|
| order-service | orders + history | `BaseOrderService` → `OrdersService`, `OrderHistoryService` |
| portfolio-service | positions + pnl | `BasePortfolioService` → `PositionsService`, `PnlService` |
| auth-service | login + users | `BaseAuthService` → `AuthService`, `UserService` |
| market-data-service | prices only | `MarketDataService extends BaseService` |

```java
public final class OrdersService extends BaseOrderService {
    public static ApiResponse placeMarketOrder(String token, String symbol, Side side, BigDecimal quantity) {
        OrderRequest order = OrderRequestBuilder.anOrder().type(OrderType.MARKET).symbol(symbol).side(side).quantity(quantity).build();
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH)
                .token(token)
                .body(order)
                .build());
    }
    
    public static ApiResponse placeOrder(String token, OrderRequest orderRequest) {
        return execute(serviceUrl(), ApiRequest.builder()
                .method(HttpMethod.POST)
                .path(BASE_PATH)
                .token(token)
                .body(orderRequest)
                .build());
    }
}
```

### 8.5 WebSocket service objects
Parallel **WS service objects** for e2e, targeting the app's **STOMP/SockJS** notification
gateway. A static method opens a STOMP session and returns a stateful handle that captures
frames:

```java
public final class NotificationsWsService extends BaseWsService {
    public static WsSession subscribePrices(String token, List<String> symbols) { ... } // -> /topic/prices.*
    public static WsSession subscribeFills(String token) { ... }                        // -> /user/queue/fills
    // WsSession: nextFrame(Duration), frames(), close(); frame queue for Awaitility.
}
```

`BaseWsService` mirrors `BaseService`: it is the **only** place the STOMP client
(`WebSocketStompClient`/`SockJsClient`) is referenced (ArchUnit §13.1), builds the WS URL from
`Config`, logs handshake URL + subscribe destination + every received frame, and attaches the
frame log to Allure. No defensive checks — negative cases (bad token, bad destination) reach
the real gateway.

### 8.6 UI tests reuse service objects
UI e2e tests use the same service objects for fast data arrange/cleanup, and Playwright only
for UI assertions. e2e tests (API and UI) call **only** `*Page` and `*Service` methods —
never REST-Assured/`HttpClient`/Playwright APIs directly (§13).

---

## 9. WireMock Usage Detail

| Mocked | Transport | Showcase point |
|---|---|---|
| Exchange order routing | REST | request matching + stateful scenarios |
| Exchange price feed | **WebSocket (beta)** | the headline WS-mock demo |
| Exchange auth/heartbeat | REST | scenario state + fault injection |

Also demonstrates **fault injection** (drop WS mid-stream, malformed frame, delay) to test
market-data-service resilience.

> ✅ **Spike done (M0, `wiremock-ws-spike`):** WireMock 4.0.0-beta.36 accepts WS connections,
> matches inbound frames via `messageStubFor(message()…willTriggerActions(sendMessage(…)
> .onOriginatingChannel()))`, and pushes one or many server frames (echo + subscribe→price-burst
> proven). The `org.wiremock:wiremock` artifact pulls the WS support transitively; the raw WS
> client (`org.java-websocket`) drives it in tests. Fallback (embedded WS stub) no longer needed.

---

## 10. Cross-cutting Implementation Notes
- **Money:** `BigDecimal` `Money` wrapper, `ROUND_HALF_EVEN`; never `double`; format at edge.
- **Time:** injected `Clock`; fixed clock in tests.
- **IDs:** UUID order IDs; seeded where asserted.
- **Migrations:** Flyway per service; Testcontainers runs real migrations.
- **Config (app):** Spring profiles `local` (compose) vs `test` (Testcontainers).

---

## 11. Configuration & Reporting for e2e

### 11.1 Config singleton + property precedence
Singleton `Config.get()` resolves every property with strict precedence:

```
1. -D system property        (e.g. -Dui.browser.headless=false)
2. environment variable      (e.g. UI_BROWSER_HEADLESS=false)
3. application-<env>.properties   (env chosen by `env` property, default `local`)
```

`application-local.properties`, `application-docker.properties`, `application-ci.properties`
in `e2e-tests`. Keys: `base.url`, `ws.base.url`, per-service base paths, credentials;
`ui.browser`, `ui.browser.headless`, `ui.browser.mode` (local|testcontainers),
`ui.browser.channel`, `ui.video`, `ui.trace`, `ui.screenshot.on.failure`.

### 11.2 Allure steps & failure artifacts
- Each step wrapped in `Allure.step("...")`; service objects add request/response attachments.
- **API failures attach:** full captured request/response log (full URL, body, headers,
  params, cookies, status, response body, timings).
- **UI failures attach:** full log + screenshot + video + console logs + **Playwright trace** +
  network log.
- A JUnit 5 extension in `framework` harvests the per-test log buffer + Playwright artifacts on
  failure and calls `Allure.addAttachment(...)`. On success, heavy artifacts discarded
  (`ui.trace=on|retain-on-failure|off`).

---

## 12. Local Dev & Running the App
- **FE devcontainer** (`frontend/web-dashboard/.devcontainer/devcontainer.json`): Node image
  with pnpm preinstalled — no npm/pnpm on the host.
- **docker-compose.yml** (root): all BE services + Postgres + Kafka + Redis + FE + a WireMock
  exchange container seeded with a looping price feed; live dashboard for manual play and a
  real stack for `-Papi-e2e`/`-Pui-e2e`.

---

## 13. Architecture & Quality Gates

### 13.1 ArchUnit (structure — JVM bytecode)
**Services:**
- Layering `controller → service → repository`; no reverse deps; no controller↔controller.
- No cross-service package dependencies.
- Naming: `*Controller`/`*Service`/`*Repository`; repositories are interfaces.
- No field injection; no `java.util.Date`; money uses `Money`, not `double`/`Double`.

**e2e-tests module:**
- Service objects extend `BaseService` (or a `Base*Service`), live in `...framework.services`,
  methods are `static`, return type is `ApiResponse`.
- Multi-functionality: `*Service` extends a `Base*Service`.
- **No defensive programming in framework/services:** no calls to `Objects.requireNonNull`,
  no dependency on validation libs (`jakarta.validation..`, `org.apache.commons.lang3.Validate`),
  no `throw new IllegalArgumentException/NullPointerException` in `...framework..` (custom
  `ArchCondition` over method call/instantiation targets).
- Page Objects in `...framework.pages`, named `*Page`, **only constructors that take a Playwright
  `Page`** exist.
- **Encapsulation:** classes in `...e2e.api` / `...e2e.ui` may depend only on `*Page` and
  `*Service` types; **must not** import `com.microsoft.playwright..`, REST-Assured, or
  `java.net.http..` directly.
- **Engine isolation (the facade payoff):** only `BaseService` may depend on
  `io.restassured..`; only `BaseWsService` may depend on the STOMP client
  (`org.springframework.web.socket..`/`...messaging.simp..`/SockJS). Every other class — service
  objects, tests, pages — is transport-agnostic, so swapping REST-Assured→`HttpClient` or the
  STOMP client touches exactly one class.
- `ApiRequest`/`ApiResponse` are records (immutable).

**Implementation:** the shared rules are libraries in `testing/test-rules` (`ServiceArchitectureRules`,
`TestAssertionRules`, `IntegrationTestRules`); each module runs them from a small class naming its package
(`<Svc>ArchitectureTest`, `<Module>TestArchitectureTest`). `E2eArchitectureTest` holds the e2e rules above and
includes `TestAssertionRules`.

### 13.2 PMD / Checkstyle (statement-level — what ArchUnit cannot see)
ArchUnit inspects dependencies, **not** method-body control flow, so the statement-level test
rules are enforced with PMD custom rules / Checkstyle, wired into the same `verify` gate:
- **Ban control statements in `*Test`/`*IT` classes:** no `if`, `else`, `switch`, `for`,
  `for-each`, `while`, `do/while`, conditional/ternary, and no `try/catch` wrapping expected
  successes. (PMD AST rule scoped to test source roots.)
- **Encourage parameterization:** a PMD rule flags duplicated test-method bodies / magic-value
  fan-out that should be a `@ParameterizedTest`. (Heuristic — pure parameterization can't be
  *proven* statically, so this is a lint warning + review guideline, stated honestly.)
- `Thread.sleep` / `TimeUnit.sleep` in tests: banned in every module by ArchUnit
  (`TestAssertionRules.testsNeverSleep`), since it is a method call, which bytecode sees.
- The rule → tool mapping actually in force is conventions.md, "Enforced by".

### 13.3 Frontend (TS — ArchUnit is JVM-only)
Equivalent guarantees via **dependency-cruiser** (layer/boundary rules: components → hooks →
api-client; no deep cross-feature imports) and **eslint-plugin-boundaries**, run in the FE
devcontainer and CI. The doc states ArchUnit's JVM-only limitation explicitly — this is the
honest substitute.

---

## 14. Resolved Decisions
1. **WS protocol** — **raw WS JSON for the mocked exchange feed**, **STOMP over SockJS for the
   app's notification gateway** (§6).
2. **e2e HTTP engine** — **REST-Assured**, confined to `BaseService.send`; swappable to JDK
   `HttpClient` via that one method; isolation enforced by ArchUnit (§8.3, §13.1).
3. **Kafka Testcontainers flavor** — **Confluent `cp-kafka`**.
4. **WireMock** — latest **beta** (the only non-stable dep) for WS mocking; M0 spike validates
   the beta WS DSL; embedded-WS-stub fallback documented (§9).
5. **auth-service** — **thin-but-real**: a small service issuing JWTs + demo users, so the
   Playwright login and `AuthService`/`UserService` e2e tests are honest.
6. **Playwright** — latest **stable**; Testcontainers browser-server image tag pinned to the
   same Playwright version (§7.4).

All other dependencies: **latest stable** at scaffold time, versions centralized in the parent
`pom.xml`.

---

## 15. Build Order (milestones)
1. **M0 — Spikes:** ✅ toolchain pinned & verified (Java 25.0.3, Maven 3.9.11, Spring Boot 4.1.0
   BOM → JUnit 6.0.3 / Testcontainers 2.0.5 / Jackson 3.1.4; REST-Assured 6.0.0, Playwright
   1.60.0, ArchUnit 1.4.2, Allure 2.35.2, WireMock **4.0.0-beta.36**). ✅ **WireMock-WS-beta
   runtime proof done** via the `wiremock-ws-spike` module (echo + subscribe→price-burst); §9
   risk retired, raw-WS-for-exchange decision validated.
2. **M1 — Skeleton:** ✅ Maven reactor + `e2e-tests` module with the framework built & green
   (ApiRequest/ApiResponse incl. `as`/`asList`, `Config` singleton w/ precedence, `BaseService`
   with REST-Assured isolated, `BaseWsService` STOMP, service objects for auth/order/market-data,
   Page-Object base + `PlaywrightManager`, Allure hook). 14 framework self-tests pass.
   ✅ **order-service** (Spring Boot 4) added: REST place/cancel/history, Postgres+Flyway,
   exchange routing via REST, `orders.submitted` → Kafka. Per-service test green (Testcontainers
   Postgres + cp-kafka + WireMock REST): place→persist→route→publish, invalid→400. Reactor: 18
   tests across 3 modules. (Modular-autoconfig gotchas captured in §4.0.)
3. ✅ **M3 — Market data + WS mock:** market-data-service consumes the WireMock **raw-WS** exchange
   feed (subscribe→price-burst), caches latest in Redis, publishes `price.ticks` to Kafka, serves
   `GET /prices/{symbol}`. Headline WS-mock per-service test green (Testcontainers cp-kafka + Redis
   + WireMock WS). Reactor: 4 modules, 19 tests. (Fixed compiler `-parameters` — §4.0.)
4. ✅ **M4 — Portfolio + notification:**
   - **portfolio-service**: consumes `fills` + `price.ticks`, maintains positions/avg-price and
     unrealized P&L (BigDecimal, Postgres+Flyway), serves `GET /portfolio`. Per-service test
     (Testcontainers Postgres + cp-kafka) green: fill→position, tick→mark price, P&L = 50.
   - **notification-service**: STOMP-over-SockJS gateway; `@KafkaListener` fans `price.ticks` →
     `/topic/prices.<symbol>` and `fills` → `/topic/fills`. **Real WS integration test** green: a
     STOMP client receives a Kafka-sourced tick — exercises the app's own WS side with the same
     STOMP client the e2e framework uses. Reactor: 6 modules, 21 tests.
5. ✅ **M5 — Cross-service flagship** (`integration-tests`): order + market-data + portfolio +
   notification launched as **isolated subprocesses** (from `-boot` jars) against shared
   Testcontainers infra (2× Postgres, cp-kafka, Redis) + two WireMock servers (REST + raw WS).
   Green chain: `POST /orders` → WireMock REST routing → `orders.submitted` → market-data matches
   at the WS-fed price → `fills` → portfolio position (`GET /portfolio`) → STOMP push to a
   subscribed client. Also added **order-matching** to market-data (`orders.submitted` → `fills`).
   Reactor: 7 modules; full `mvn verify` green. Integration gotchas captured in §4.2.
6. ✅ **M6 — API + WS e2e:** facade service objects now resolve **per-service base URLs**
   (`order.base.url`, `market-data.base.url`, `portfolio.base.url`, `auth.base.url`) since the
   backend is multiple services on different ports; added the portfolio service object +
   `PositionView`. Black-box suite `com.example.demo.e2e.api.TradingTest` (Allure-stepped, parameterized over
   AAPL/MSFT/GOOG) **runs against a running stack** (`RequireRunningBackend` skips if down — see
   the §7.1 e2e principle): place order → portfolio position → latest price; invalid order → 400;
   **fill pushed over STOMP WS** (asserted via the order id). Run with `-Papi-e2e` after
   `docker compose up`; verified green (6 tests). 14 framework self-tests green.
   *(Deferred: auth-service + AuthService/UserService e2e — services don't enforce tokens yet.)*
7. ✅ **M7 — Frontend + docker-compose:** React 19 + Vite + TS dashboard (`frontend/web-dashboard`):
   live ticker (STOMP/SockJS), order ticket (React 19 `useActionState` form action), portfolio
   panel (polling + live P&L), recent fills. Builds clean (tsc + Vite). **Devcontainer**
   (`typescript-node:24`) so no host npm/pnpm. Per-service **Dockerfiles** (temurin:25-jre + `-boot`
   jar) + FE multi-stage (node→nginx, nginx reverse-proxies `/api/*` and `/ws` to the services).
   **docker-compose.yml**: Postgres (2 DBs via init), cp-kafka (KRaft), Redis, WireMock exchange
   (REST routing), 4 services, FE. market-data runs a **demo feed mode** (synthetic prices) so the
   stack is live without an exchange. **Verified end-to-end in containers**: dashboard at
   :8088, live prices, place order → SUBMITTED → portfolio position with live P&L.
8. ✅ **M8 — UI e2e (Playwright):** Page Objects `LoginPage`/`DashboardPage` (explicit `Page` ctors;
   Playwright locators/assertions confined to the pages). `com.example.demo.e2e.ui.TradingTest` (Allure-stepped) drives
   the real dashboard in a browser — login → place order via the order ticket → AAPL position appears in
   the portfolio panel — then cross-checks via the `PortfolioService` object. `PlaywrightLifecycle`
   manages the browser (local/Testcontainers, headless from `Config`) and attaches **screenshot +
   console + trace + video** to Allure on failure; skips if the stack is down. Tagged `ui`,
   excluded from default `verify`, run via **`mvn -pl e2e-tests -Pui-e2e verify`** against
   `docker compose up`. **Verified green** with real Chromium against the compose stack.
9. ✅ **M9 — Quality gates:**
   - **ArchUnit (e2e framework)** — `E2eArchitectureTest`, 8 rules: REST-Assured confined to
     BaseService, STOMP to the ws package, Playwright to ui/pages/harness/arch, no bean-validation
     (no-defensive), ApiRequest/ApiResponse are records, service objects extend BaseService with
     static methods, Page Objects have only explicit `Page` constructors. Rules live in-module (classes on
     the classpath) rather than a separate `archunit-tests` module.
   - **ArchUnit (service)** — `OrderArchitectureTest`, 5 rules: `*Controller` in `..web..`,
     `*Repository` interfaces in `..domain..`, no field injection, no standard streams, no `java.util.Date`.
   - **PMD control-flow ban** — `build/pmd/test-conventions.xml` (XPath banning if/for/while/do/
     switch/ternary/try in test sources; main + harness excluded), wired into the parent build for
     every module's `verify`. **PMD 7.25.0** pinned (the plugin's bundled 7.7.0 can't parse Java 25).
     Verified it fires on a deliberate violation and passes on all real tests.
   - **Frontend** — **dependency-cruiser** (`.dependency-cruiser.cjs`): no cycles, data layer
     (api/ws) must not import UI components, no orphans. Green (17 modules cruised).
10. ✅ **M10 — Polish + hardening (all except CI):**
    - **Enforced auth:** new `auth-service` (HS256 JWT issuer + demo users); a shared-secret JWT
      resource server enforced on **all** services (REST via `common.ApiSecurityConfig`, the
      notification **WebSocket** via a STOMP-CONNECT interceptor). FE has a real login; every test
      layer mints/obtains a token (per-service incl. 401 tests, flagship, API/UI e2e via login).
    - **Shared `Money`** value type (`common`) used by portfolio (P&L) + market-data (prices), with
      a JPA converter; fixed-point HALF_EVEN, BigDecimal at the JSON/DB edges.
    - **ArchUnit** added for market-data/portfolio/notification/auth. **Allure-maven** report.
      **WireMock WS fault-injection** demo (malformed frame / no-match). **Checkstyle** (import
      hygiene, Checkstyle 13.x for Java 25) + **ESLint** (Rules of Hooks + boundaries) for the FE.
    - **Docs:** root `README.md` + `docs/testing-cookbook.md`.
    - Full `mvn verify` green (10 modules); API e2e (6) + UI e2e (1) verified against the compose
      stack with auth. **CI pipeline intentionally not built.** `test-support` extraction is moot
      (e2e boots nothing; only `integration-tests` launches subprocesses). *(Later revisited: the shared
      IT plumbing now lives in `testing/test-support`.)*
11. ✅ **M11 — Trading-domain coverage (BVA/EP, reviewed with the grizzled-trader):**
    - **Order BVA/EP:** parameterized validation matrix — quantity `0` (off-point of `@Positive`),
      `-5`, missing; symbol `""`/`"   "` (`@NotBlank`); null & bad-enum side; valid boundaries
      (`0.0001`, large/no-cap); both BUY/SELL accepted.
    - **Cancel lifecycle:** live → CANCELLED, idempotent re-cancel, unknown → 404; order history.
    - **Matcher invariants (market-data):** exactly-one-**full**-fill; unknown symbol → **no fill**
      (the silent "no price → no fill" trap).
    - **SELL-side math (portfolio):** sell reduces long with avg unchanged; sell-more-than-held goes
      **negative** (no short guard); weighted-average across buys; P&L direction (+/0/−). Distinct
      symbol per test (positions accumulate in the shared context).
    - **e2e (minimal overlap):** WS-fill and UI order ticket tests parameterized over **BUY+SELL** to prove
      the chain/UI carry a SELL; SELL *arithmetic* stays at the service layer.
    - **Out of scope by design** (features absent, not tested as fiction): order types (limit/stop),
      partial fills, working-hours gate. Documented, not implemented. *(Most were implemented in M12.)*
12. ✅ **M12 — Order types, working-hours gate, short-sell guard:**
    - **LIMIT/STOP orders:** `OrderType` + `limitPrice`/`stopPrice` (DTO/entity/V2 migration,
      cross-field `@AssertTrue` validation), carried on `orders.submitted`. The market-data **matcher**
      fills MARKET always, LIMIT at-or-through the limit, STOP once triggered (null type = MARKET, so
      legacy events still fill).
    - **Working-hours gate:** shared `common.MarketHours` (NYSE-like 09:30–16:00 ET, Mon–Fri) +
      `MarketHoursPolicy` (toggle via `trading.market-hours.enabled`); off-hours → **422**. The
      injected `Clock` is replaced by a `MutableClock` in `OrderServiceIT` to test open/closed.
    - **Short-sell guard:** pre-trade `PortfolioClient` lookup (service-token auth) of held quantity
      against a new portfolio `GET /portfolio/holdings/{symbol}`; oversell → **422**.
    - **e2e gating:** `@RunDuringMarketHours` (+ `MarketHoursCondition`) skips order-placing tests
      outside hours with a reason **recorded in Allure**; `market.always-open` / server
      `TRADING_MARKET_HOURS_ENABLED` overrides allow any-time CI runs. SELL e2e seed a position first.
    - **Verified:** full `mvn verify` green (OrderServiceIT 28, MarketDataServiceIT 7,
      PortfolioServiceIT 9, MarketHoursTest 7, flagship with the gate disabled); API e2e (7) + UI e2e
      (2) pass with the override, and skip-with-message confirmed in `target/allure-results`.
    - **React order ticket** now supports order types: a type selector reveals a conditional
      limit/stop price field (MARKET sends neither). `placeOrder` takes an `OrderRequest`; the
      Playwright `DashboardPage` drives typed orders, and `com.example.demo.e2e.ui.TradingTest` places a marketable
      LIMIT/STOP BUY through the dashboard and asserts it fills.
    - **Still out of scope:** partial fills (matcher emits one full fill).
    - Fixed: Allure results now write to `target/allure-results` (via `allure.properties`) so
      `mvn allure:report` renders them.
13. ✅ **M13 — Edge-case coverage (2nd grizzled-trader review):**
    - **Bug fixed:** order-service published `orders.submitted` even for REJECTED orders → the matcher
      would fill a rejected order (phantom position). Now only SUBMITTED orders publish; an IT asserts
      REJECTED → no publish.
    - **Matcher (market-data IT 7→16):** all four directions BUY/SELL × LIMIT/STOP at the inclusive
      `≤`/`≥` **boundary** (market == limit/stop) and clearly marketable; every fill asserted **at the
      market price** (LIMIT price improvement); SELL-STOP-never-triggers silent drop; marketable LIMIT
      on an uncached symbol → no fill, no NPE (no-price guard runs first).
    - **order-service IT 28→31:** short-sell **boundaries** (sell == held → 201; zero holdings → 422);
      exchange-REJECTED + no-publish.
    - **e2e (one seam):** a marketable LIMIT BUY's portfolio average proves it filled at market, not at
      the limit — price improvement survives order→matcher→fill→portfolio.
    - **Known gaps, noted not built:** Kafka-redelivery fill idempotency (no dedup); cancel-after-fill
      (order-service has no fill state, so no observable distinction).
14. ✅ **M14 — Final edge-case sweep (3rd grizzled-trader review, then stop):** three targeted ITs,
    each the documented error/security path whose happy twin was already tested —
    `GET /prices/{unknown}` → 404 (market-data); `limitPrice`/`stopPrice` ≤ 0 → 400 (order, proving
    `@Positive` fires on the price fields, distinct from the cross-field rule); and **STOMP CONNECT
    with a forged JWT (wrong secret) → rejected** (notification — the WS security boundary, the
    highest-signal remaining gap). Investigated and **deliberately skipped** (would lock in
    accidental/absent behaviour, per the trader): sub-scale-4 quantity precision (`numeric(19,4)`
    rounding is a code decision, not a contract) and an exchange transport-fault branch (no
    catch/fallback exists — a fault rolls back before publish). Counts: Order 35, MarketData 17,
    Notification 2. The trader's call: coverage is now at the point of diminishing returns — stop.
15. ✅ **M15 — Authorization (per-user) + auth-token security tests:**
    - The services previously only **authenticated** (valid JWT) with no per-user **authorization**.
      Added **order ownership**: each order's `owner` is the JWT subject (set server-side via
      `Authentication.getName()`, never from the body — V3 migration adds the column). `/orders/history`
      is now **owner-scoped** (`findByOwner`), and cancel only affects your own order (a non-owner gets
      **404**, so existence isn't leaked). Positions stay an aggregate book view (documented).
    - **order IT 35→41:** invalid tokens → 401 (expired / forged-wrong-secret / malformed); order owned
      by subject (body `owner` ignored); history scoped to the user (Bob can't see Alice's order);
      cannot cancel another user's order (404).
    - **API e2e 8→10:** two real logins — Alice's trades are invisible to Bob; an expired token → 401
      against the deployed services.
    - (WS forged-token rejection was already added in M14.) Full `mvn verify` green; flagship still
      green with owner-attributed orders.
16. ✅ **M16 — UI negative + field-validation tests (network interception, grizzled-trader reviewed):**
    - New `OrderTicketPage` owns Playwright **network interception** (stub any status / abort / hang on
      `/api/orders`) so the tests stay Playwright-free (ArchUnit holds). New `OrderTicketNegativeUiIT`
      (7 methods, 10 invocations), **not** market-hours-gated (the POST is intercepted).
    - Scenarios: a rejected order (400/401/422/500) shows `Order rejected (<status>)` and re-enables the
      button; the order ticket **locks while in flight** (disabled, exactly one request — no double-fire);
      a backend **abort** surfaces an error and isn't left looking submitted; a **hung** backend keeps
      the button locked with no false outcome (flags the absence of a client timeout); the order type
      reveals only the matching price field; quantity below `min` is invalid; and a **silent success**
      (no error + one request) pins the trust model.
    - The trader's core finding: **success is silent** on this order ticket (the only signal is the absence of
      a red line), so the high-value tests prove the UI never looks idle/submitted when the order failed.
      Noise deliberately left to service tests: the exact 400 matrix and the two 422 causes (the UI
      collapses both to one path). Verified against the live stack (UI e2e 14 total).
17. ✅ **M17 — All order types + pending (non-marketable) orders, UI and API e2e:**
    - **UI**: `createsAndFillsEachOrderTypeThroughTheTicket` (MARKET/LIMIT/STOP, marketable → accepted,
      no error, position appears — `DashboardPage.placeOrder` now also handles MARKET) and
      `pendingTypedOrderIsAcceptedButDoesNotFill` (LIMIT @ 0.01 / STOP @ 1000000 — non-marketable BUY:
      order ticket accepts it with no error, but the GOOG position quantity stays **unchanged**, i.e. it
      never fills — there is no resting book).
    - **API e2e (the same pending scenario)**: `pendingTypedOrderIsSubmittedButNeverFills` — 201
      SUBMITTED, then **no fill is pushed over WebSocket** for that order id within the window.
    - Both pending tests are `@RunDuringMarketHours` (they place real orders). Verified live: API e2e
      12, UI e2e 7 (+ negative 10).
18. ✅ **M18 — Order status lifecycle (SUBMITTED → FILLED / PENDING) + DB-asserting integration tests:**
    - **Status model:** `OrderStatus` is `NEW, SUBMITTED, PENDING, FILLED, REJECTED, CANCELLED`
      (`NEW` is the transient creation state; an accepted order is **SUBMITTED** — kept as the accepted
      state). order-service publishes a SUBMITTED order; a new `OrderOutcomeConsumer` listens to
      `fills` → **FILLED** and to a new `orders.resting` topic → **PENDING** (idempotent, never
      overrides CANCELLED). The **market-data matcher** now emits an `orders.resting` event for
      non-marketable / no-price orders so the resolution can happen.
    - **Integration tests assert the DB**, not just the API: `OrderServiceIT` (41→43) injects
      `OrderRepository` and asserts the **persisted status** — SUBMITTED on placement, FILLED after a
      `fills` event, PENDING after an `orders.resting` event, REJECTED on decline, CANCELLED after
      cancel. `MarketDataServiceIT` (17→18) asserts the resting-event emission.
    - **Cross-service:** the flagship places → SUBMITTED, then asserts the order resolves to **FILLED**
      in order-service's own store (it consumes the fill back) — a real DB-of-record check across
      services. API e2e: `placingAnOrder` → SUBMITTED then FILLED (via history); the pending test →
      SUBMITTED then PENDING.
    - Full `mvn verify` green; API e2e 12 verified live (SUBMITTED→FILLED and SUBMITTED→PENDING).
19. ✅ **M19 — User registration + field-validation / negative-login tests across every layer:**
    - **Feature:** auth-service gains `POST /auth/register` (`username`/`email`/`password`; confirm is
      FE-only). Users now live in **Postgres + Flyway** (`authdb`); the in-memory `DemoUsers` became a
      JPA `UserEntity`/`UserRepository`/`UserDirectory`, with passwords stored as **BCrypt** hashes.
      Flyway seeds alice/bob/demo with bcrypt(`password`) so existing logins keep working. Validation =
      house style (`jakarta.validation` + `@Valid` → 400; duplicate username/email →
      `ResponseStatusException(409)`). The FE gains a `Register` form (native HTML5 + confirm-match) and
      an `Auth` login/register toggle; `docker-compose` adds `authdb` + the auth datasource.
    - **Integration:** `AuthServiceIT` (4→21) now runs on a Postgres Testcontainer — register (201 +
      token), register-then-login, duplicate username/email/seeded → 409, ~9 invalid-field → 400,
      blank-login → 400, unknown-user → 401.
    - **API e2e:** `AuthApiE2eIT` — register→login, duplicate 409, representative 400, login 401/400
      (deployed wiring; the exhaustive matrix stays in the IT — minimal overlap).
    - **UI e2e:** `AuthNegativeUiIT` (Playwright, network interception) — login 401 error, registration
      native-validation (invalid email / short password block submit, no request), confirm-mismatch
      disables submit, 409 message, and a real register→dashboard happy path.
    - **Frontend tests (new tooling):** **Vitest + Testing Library** (13 unit tests: `client` fetch
      mapping, `passwordsMatch`, `Login`/`Register`/`Auth` in jsdom) and **WebdriverIO** browser
      component tests (6: `Login`/`Register` in real headless Chrome — the native-validation cases jsdom
      can't enforce). Test files excluded from `tsc`/eslint-boundaries/dependency-cruiser so the existing
      FE gates stay green.
    - Full `mvn verify` green; FE unit 13 + component 6 green; auth API/UI e2e verified live.
20. ✅ **M20 — Dedicated security e2e suites (API + UI):**
    - `SecurityApiE2eIT` (11) — **authentication**: forged (wrong-secret) token, malformed bearer
      token, missing `Bearer` prefix, no token → all 401; SQL-injection-style login → 401 (never 500).
      **Broken access control**: an order's owner is the JWT subject, not a spoofed body field; a user
      cannot cancel another's order (404, not 403). Complements the existing expired-token /
      cross-user-read tests in `com.example.demo.e2e.api.TradingTest` (no duplication; the portfolio was still global at this
      point, so no portfolio-isolation test — **M21** later made it per-user and added that coverage).
    - `SecurityUiE2eIT` (3) — the JWT is never written to local/session storage (stays in React
      state); logout clears the session; a username containing an `<img onerror>` payload renders as
      text (React output-encoding), not a live element.
    - Also refactored the Playwright network-interception boilerplate into `framework.ui.RouteStub`
      (a counting stub) behind `BasePage.intercept(glob)`, shared by the Order/Login/Registration
      page objects. All e2e ArchUnit/PMD/Checkstyle green; both suites verified live.
21. ✅ **M21 — Per-user portfolio (positions, holdings, short-sell guard scoped by owner):**
    - **Owner now propagates the whole chain**: order-service stamps the order `owner` (JWT subject)
      onto `orders.submitted`; the matcher carries it onto `fills`; portfolio attributes the position
      to that owner. (`FillEvent`/`OrderSubmittedEvent` records gained an `owner` field across
      order/market-data/portfolio; notification just relays the raw fill, unaffected.)
    - **portfolio-service is per-user**: `PositionEntity` is keyed by a composite `(owner, symbol)`
      (`@IdClass PositionId`); the Flyway schema PK is `(owner, symbol)`. `GET /portfolio` and
      `GET /portfolio/holdings/{symbol}` are scoped to `authentication.getName()`, so one user never
      sees another's book. A price tick marks the symbol for every owner holding it.
    - **Short-sell guard is per-user**: order-service mints the holdings-lookup token with the order's
      **owner** as subject, so a SELL is validated against the seller's own holding (not a global book).
    - **Tests**: `PortfolioServiceIT` (9→10) — fills now carry an owner, plus a new
      `positionsAreScopedToTheOwner` (two owners, same symbol → two separate books). The per-user
      change also surfaced and fixed a latent UI-test bug (`placeOrderThroughTheDashboard` seeded as
      `demo` but sold as `alice`). Full `mvn verify` green (flagship included); all API + UI e2e
      verified live. **Note:** this makes the portfolio-isolation coverage real — previously skipped
      because the book was global.
22. ✅ **M22 — Coverage-gap pass across every layer (analysis-driven; grizzled-trader consulted):**
    - **Backend integration:** `PortfolioServiceIT` (10→16) — one price tick marks **all** owners of a
      symbol; **short-position P&L sign** (the only path through `(mark−avg)×qty` with negative qty);
      flip long→short keeps the long average; sell-to-flat→rebuy resets the average. `OrderServiceIT`
      (43→45) — the M18 outcome-consumer invariants: a fill for a CANCELLED order stays CANCELLED; a
      resting event for a FILLED order stays FILLED. `NotificationServiceIT` (2→5) — `fills` →
      `/topic/fills`, one tick fanned out to multiple subscribers, and a malformed tick doesn't stall
      the gateway.
    - **Frontend (Vitest 13→32):** `client` error branches (`placeOrder`/`getPortfolio` throw,
      `getPrice` 404→null); `PortfolioPanel` (empty state, P&L up/down coloring, null mark, refetch on
      `version`); `OrderTicket` (conditional limit/stop reveal, MARKET body omits prices, rejection
      error); `Ticker`/`Fills` rendering + coloring.
    - **E2E API:** `com.example.demo.e2e.api.TradingTest` (12→14) — order-cancellation happy path (place→cancel→CANCELLED in
      history) and live prices streamed over STOMP (`/topic/prices.<sym>`).
    - **E2E UI + harness refactor:** `PlaywrightManager` gained a **second isolated browser context**
      (`openSecondContext`/`useFirst`/`useSecond`; artifacts stay on the primary). New
      `MultiUserPortfolioUiE2eIT` drives alice and bob in two simultaneous contexts and proves each
      sees only their own per-user portfolio.
    - Deliberately skipped as over-testing (per the grizzled-trader): cancel-a-pending order, repeated
      SELLs against the stubbed guard, avg-at-exactly-zero, sub-scale-4 rounding, the unobservable
      short-sell propagation race. Full `mvn verify` green; FE unit 32; all e2e verified live.
23. ✅ **M23 — Remaining identified test cases implemented (the full backlog from the M22 analysis):**
    - **BE unit:** `MoneyTest` (4→12: subtract, compareTo, HALF_EVEN ties, toString, negatives);
      `JwtTest` (1→3: decode round-trip + wrong-secret rejection); new **`PlaceOrderRequestTest`** (7:
      `typeOrDefault` + the `@AssertTrue` limit/stop rules); extracted **`MatchingRules`** from
      `OrderMatcher` (behaviour-preserving) with **`MatchingRulesTest`** (13: directional LIMIT/STOP
      boundaries unit-tested directly instead of only through the Kafka IT).
    - **BE integration:** `OrderServiceIT` (45→48: cancel a PENDING order, cancel a FILLED order
      [cancel has no fill-state guard — the mirror asymmetry of the consumer], an exchange 5xx rolls
      back with no persist/publish); `AuthServiceIT` (21→24: `GET /auth/users/{id}` success + 404, an
      expired token → 401).
    - **Frontend (Vitest 32→38, WDIO 6→8):** the **`useMarketData`** STOMP hook (mocked client:
      no-token, bearer connect, per-symbol subscribe, price→state, fills 20-cap, deactivate on
      unmount); `App` session/login/logout; `OrderTicket.wdio` native number-input validation.
    - **E2E API (`com.example.demo.e2e.api.TradingTest` 14→17):** a newly-registered user's onboarding→trade→portfolio
      journey; a position carries a live market price + P&L through the stack; cancelling an
      already-cancelled order is idempotent. **E2E UI:** new `LiveDashboardUiE2eIT` — the streamed
      ticker shows a price, a fill appears in the fills panel, and switching users in one browser shows
      a different (empty) portfolio (no stale state).
    - Genuinely unimplementable and still skipped: the short-sell *propagation race* (only honest in
      the flagship, inherently flaky) and **sub-scale-4 rounding** (M14 ruled it undefined behaviour —
      testing it would pin an arbitrary code decision). Full `mvn verify` green; FE unit 38 + component
      8; all e2e verified live.
24. ✅ **M24 — Postgres startup-race fix + cash funds (deposit/withdraw) with a pre-trade funds guard:**
    - **Startup flakiness fixed.** The Postgres healthcheck was `pg_isready -U postgres` (Unix socket),
      which reports "healthy" during first-boot init — while the entrypoint runs a temporary
      socket-only server to apply `init.sql`, *before* the real TCP listener is up. Dependents
      connecting to `postgres:5432` over TCP then hit "connection refused"; whichever DB service lost
      that race exited, and auth-service exiting cascaded (nginx couldn't resolve its upstream → the FE
      container died too). Fix: the healthcheck now gates on **TCP** (`pg_isready -h 127.0.0.1` +
      `start_period`), which only accepts once init has finished; plus `spring.flyway.connect-retries`
      on auth/order/portfolio as a safety net. Verified across repeated fresh `down -v` + `up` cycles.
    - **Funds (portfolio-service).** A per-user cash balance (`balances` table, seeded for alice/bob/
      demo; new users start at 0). `POST /funds/deposit`, `POST /funds/withdraw` (overdraw → 422),
      `GET /funds/balance`, scoped to the JWT subject; `@Positive` amount → 400. Fills move cash (a BUY
      spends, a SELL receives) via the same consumer that builds positions.
    - **Pre-trade funds guard (order-service).** A BUY is valued at `quantity × latest market price`
      (a new `MarketPriceClient` reads market-data; 404 → unvalued → skipped) and rejected with 422
      when it exceeds the owner's cash (`PortfolioClient.availableCash`) — mirroring the short-sell
      guard. New `MARKET_DATA_BASE_URL` wiring; the flagship and the new-user e2e now **deposit first**.
    - **Funds UI.** `FundsPanel` (balance + deposit/withdraw) wired into the dashboard; `/api/funds`
      proxied (vite + nginx); `client.ts` gains `getBalance`/`deposit`/`withdraw`.
    - **Tests.** Unit: `FundsServiceTest` (Mockito — arithmetic + overdraw). IT: PortfolioServiceIT
      (16→25: deposit/withdraw/422/400/per-user/seed/cash-from-fills), OrderServiceIT (48→51: funds
      guard insufficient/sufficient/no-price-skips). FE: Vitest 38→48 (client funds + `FundsPanel`),
      WDIO 8→10 (`FundsPanel`). E2E: `FundsApiE2eIT` (deposit/withdraw/over-withdraw/unfunded-BUY→
      deposit→BUY) + `FundsUiE2eIT` (register → $0 → deposit → balance). Full `mvn verify` green; FE
      unit 48 + component 10; all e2e verified live.
```
25. ✅ **M25 — Per-service ITs restructured for change-scoped generation (`/it-gen`):**
    - **One class per endpoint / Kafka listener** under `<service>/it/` (e.g. `PlaceOrderIT`,
      `CancelOrderIT`, `FillsListenerIT`, `StompHandshakeIT`), replacing one `<Svc>ServiceIT` per service,
      so a changed endpoint maps to exactly one test class.
    - **Real HTTP**: MockMvc → REST-Assured on `RANDOM_PORT`. Real servlet behaviour surfaced one
      difference: an exchange 5xx now returns a real **500** (MockMvc rethrew the exception).
    - **`it/harness/`**: singleton Testcontainers (shared context across classes), WireMock servers +
      behaviour-named stub fixtures shaped like the provider DTOs, `MutableClock`, `KafkaProbe` (snapshot
      reads/polling) and `StompProbe` (sentinel handshake proves a SUBSCRIBE is live, since the simple
      broker sends no RECEIPT for it). Unmatched WireMock requests fail the test.
    - **Stricter gates**: `build/pmd/it-conventions.xml` (service modules) bans `&&`, `||`, `!`, `==`, `!=`
      and `forEach`/`filter`/`ifPresent`/`orElseGet` in tests; `<Svc>IntegrationTestArchitectureTest`
      forbids Mockito/`@MockitoBean` in ITs, keeps `*IT` in the `it` package and bans weak/non-deterministic
      assertions (`contains`, ranges, `isNotNull`/`isNotBlank`, Hamcrest `containsString`, WireMock
      `containing`): lists are made exact with a dedicated owner per test, auth resets to the seed users
      before each test, and login/registration tokens are verified by their decoded claims; JaCoCo IT coverage
      (`target/site/jacoco-it/`). *(Historical: these per-service classes have since been replaced by the
      shared `testing/test-rules` libraries run from `<Svc>TestArchitectureTest`, see §13.1.)*
    - **Tests.** ITs 127→134, all assertions kept or tightened (exact parsed Kafka/STOMP payloads instead
      of `contains`, exact error bodies, no-fill proven by the resting event instead of a fixed wait).
      Each service green on 3 consecutive runs; full `mvn verify` of common + services green.
26. 🚧 **M26 — Alerts, watchlists, risk limits + new endpoints (product code; ITs pending `/it-gen`):**
    - **alert-service (8085):** `POST/GET/DELETE /alerts`, `GET /alerts/{id}` (owner-scoped, ≤ 20 active,
      symbol validated against market-data: unknown → 422, unavailable → 503). Consumes `price.ticks`; an
      ACTIVE alert fires once (ABOVE `>=` / BELOW `<=` threshold, `@Version` guards a double trigger) and
      `alerts.triggered` is published **after commit**. notification-service pushes it to the owner over
      WebSocket (`/user/queue/alerts`) — clients are notified without polling.
    - **watchlist-service (8086):** per-user watchlists (≤ 10, ≤ 50 symbols); `GET /watchlists/{id}` prices
      all symbols with one batch call and degrades to `pricesAvailable: false` when market-data is down.
    - **risk-service (8087):** `GET/PUT /risk/limits` (defaults 100000 notional / 10000 position until
      customised) and `POST /risk/check` (notional, then resulting |position| via portfolio). order-service
      calls it after the existing guards — refusal → 422 with the reason, unreachable → 503 (fail closed);
      `risk.enabled=false` in the flagship, which doesn't boot it.
    - **New endpoints:** `GET /orders/{id}` (owner-scoped 404), `GET /prices?symbols=` (1–20, request order,
      unknown omitted), `GET /portfolio/summary` (cash, market value, unrealized P&L, position count;
      valuation moved onto `PositionEntity` and shared with `PositionResponse`), `GET /auth/me`.
    - **Verified:** full reactor `install -DskipITs` green (PMD/Checkstyle/unit/ArchUnit); the new services
      boot against Postgres (Flyway + `ddl-auto: validate`, 401 without / 200 with a JWT); existing ITs of
      auth/market-data/portfolio/notification green. Existing order ITs need the risk stub (next `/it-gen`).
