# Integration-test conventions (`/it-gen`)

The single source of truth for **it-writer** (what to produce) and **it-reviewer** (what to check),
and the **owner of the test rules** for the whole repo: the numbered [test-method rules](#test-method-rules-hard-rules)
apply to all test code in every module (e2e included, except where a rule says it is IT-only), and the
[Enforced by](#enforced-by) table says which tool checks each one. Other docs summarise and link here.
It extends `docs/testing-cookbook.md`; where they disagree, this file describes the target layout.

## Scope of a per-service IT

- Boot **one** service: `@SpringBootTest(webEnvironment = RANDOM_PORT)` (inherited from
  `testing/test-support`'s `ServiceITBase`).
- **Real** infrastructure via Testcontainers: Postgres (`postgres:17-alpine`), Kafka
  (`confluentinc/cp-kafka:7.8.0`), Redis (`redis:7-alpine`): the same images as `docker-compose.yml`
  (constants in `TestContainers`).
- **Mock only communication with other systems**: HTTP calls to other services/the exchange with a
  WireMock `WireMockServer`, the exchange WS feed with a WireMock **WS** message stub (REST and WS stubs
  on **separate** servers), and inbound Kafka messages produced by the test itself.
- **Never** mock anything internal: no Mockito, `@MockitoBean`, `@MockBean`, `@SpyBean`, or stubbed
  repositories/services/clients. The only allowed bean override is the `@Primary` `MutableClock`
  (`@Import(MutableClockConfig.class)`).
- Drive the API over **real HTTP** with **REST-Assured** (`api()` / `api(token)` from `ServiceITBase`).
  Do not use MockMvc in new or touched classes. REST-Assured and the rest of the test stack come with
  `testing/test-support`, so a writer changes no non-test file (a new service's `pom.xml` is the only
  exception, see below).

## Layout (one class per endpoint / consumer)

```
services/<svc>/src/test/java/com/example/demo/<pkg>/
  <Svc>ArchitectureTest.java       # production rules: ServiceArchitectureRules + service-specific extras
  <Svc>TestArchitectureTest.java   # test rules: TestAssertionRules + IntegrationTestRules
  it/
    PlaceOrderIT.java              # one class per endpoint: POST /orders
    CancelOrderIT.java             # DELETE /orders/{id}
    OrderHistoryIT.java            # GET /orders/history
    OrderOutcomeConsumerIT.java    # one class per Kafka listener class
    harness/
      <Svc>ITBase.java             # extends ServiceITBase: this service's containers, WireMock servers, properties, defaults
      Orders.java …                # service-specific DTO factories / helpers (e.g. order OrderMessages, portfolio PortfolioEvents)
      stubs/
        ExchangeStubs.java         # one fixture class per external dependency
        PortfolioStubs.java
        MarketDataStubs.java
```

Shared plumbing lives in **`testing/test-support`** (package `com.example.demo.testing.support`, test
scope). Reuse it; never copy it into a service:
- `it.ServiceITBase` (RANDOM_PORT, `port`, `api()`, `api(token)`), `it.ApiSpec`
- `json.JsonObjectMapper` (`INSTANCE`, `DTO_CONFIG`)
- `auth.TestJwt`: `SECRET`, `FORGED_SECRET`, `token(subject)`, `expired(subject)`, `forged(subject)`,
  `bearer(token)`, `claims(token)` → `TokenClaims`
- `containers.TestContainers`: `postgres()` / `postgres(logName)`, `kafka()`, `redis()`, the
  `datasource` / `kafka` / `redis` `DynamicPropertyRegistry` helpers, image constants
- `containers.ContainerLogs`: writes each container's output to `target/container-logs/<name>.log`
  (the `TestContainers` factories attach it)
- `kafka.KafkaProbe`: `send`, `sendRaw` (`@RawJsonPayload`), `awaitValue`, `valuesWithin`,
  `awaitRecordsWhere`, `currentRecordsWhere`; acked sends, group-less snapshot reads (the polling loop lives here)
- `stomp.StompClient` + `stomp.StompFrames<T>` (sentinel-filtering frames)
- `time.MutableClock` + `time.MutableClockConfig` (`@Import` it; starts at `TradingInstants.DURING_HOURS`)
- `fixtures.TestUsers` (`TESTER`/`ALICE`/`BOB`), `fixtures.Topics`, `fixtures.TestTimeouts.EVENT_TIMEOUT`,
  `fixtures.TradingInstants.DURING_HOURS`
- `contracts.{FillEvent,PriceTick,OrderRestingEvent}`: test DTOs used by more than one module
- `logback-test-base.xml` + a default `logback-test.xml` (a module with extra loggers writes its own
  `logback-test.xml` with `<include resource="logback-test-base.xml"/>`)

- `harness/` packages (and `testing/test-support`, which is `src/main`) are **plumbing**: excluded
  from the PMD test rules and from ArchUnit's exact-value rule (plumbing packages = `..harness..` and
  `..e2e.framework..`). Logic that a test needs (polling, parsing, token minting) goes **there**, never
  into a test.
- All eight services follow this layout (see `docs/test-coverage.md` for the class list). Reuse the
  existing `harness/` classes (`<Svc>ITBase`, `stubs/*`, service helpers such as `StompProbe`,
  `PortfolioEvents`, `OrderMessages`) and the test-support classes, and extend them rather than adding
  parallel helpers. Never touch tests of endpoints that are not in scope.
- A **new service** is a new module under `services/` with `services` as its parent pom (that brings
  `common`, `test-support`, the service PMD rulesets, JaCoCo and the boot jar; never copy another
  service's pom), plus the two small ArchUnit classes naming its package:
  ```java
  @AnalyzeClasses(packages = "com.example.demo.order", importOptions = ImportOption.OnlyIncludeTests.class)
  class OrderTestArchitectureTest {
      @ArchTest static final ArchTests testAssertions = ArchTests.in(TestAssertionRules.class);
      @ArchTest static final ArchTests integrationTests = ArchTests.in(IntegrationTestRules.class);
  }
  ```
  and `<Svc>ArchitectureTest` with `ArchTests.in(ServiceArchitectureRules.class)` (production classes).
  `RawJsonPayload` is `com.example.demo.testing.RawJsonPayload` (module `testing/test-rules`); never
  add a copy.

### `<Svc>ITBase` shape

- `extends ServiceITBase`, `@Import(MutableClockConfig.class)`; keeps only service-specific
  containers, stubs and helpers.
- Singleton containers: `static final` containers from `TestContainers` and WireMock servers started in
  a `static {}` block (not `@Container`), so every IT class of the service shares them and the cached
  Spring context.
- `@DynamicPropertySource` wires the containers (`TestContainers.datasource/kafka/redis`),
  `<dep>.base-url` and `exchange.ws-url` (see the service's `application.yaml`). It also sets **every
  dependency's `<dep>.read-timeout` short** (e.g. `"1s"`), so slow-dependency cases run fast. Every
  outbound client uses `common.http.HttpClients.withTimeouts`; the real defaults (2–5 s) would only slow
  the suite down.
- `@BeforeEach`: `resetAll()` every WireMock server, apply the **default** stubs from the fixtures, and
  set the clock to a fixed instant.
- `@AfterEach`: `assertThat(server.findAllUnmatchedRequests()).isEmpty()` for every WireMock server.
- Tokens come from `TestJwt` (`token(TestUsers.ALICE)` etc.), signed with the shared test `SECRET`.

### Stub fixtures

- One class per external dependency, static methods named after the **behaviour**:
  `acceptsOrder()`, `rejectsOrder()`, `holdings("AAPL", 10)`, `noHoldings("AAPL")`, `serverError()`,
  `slowResponse(Duration)`, `malformedBody()`, `emptyBody()`, `notFound()`.
- **Timeout cases are deterministic only far from the boundary:** stub `withFixedDelay` at **≥ 3× the
  test read timeout** (e.g. 3000 ms against 1 s) and expect 503 `<Dependency> unavailable`. Never use
  a delay close to the timeout (that's a race). And never a delay just under it to prove "slow but OK":
  assert that only with a delay far below the timeout.
- The stub body is a DTO serialized with `Json.write`, and it must match the **provider's real DTO**
  (e.g. `portfolio/web/HoldingResponse.java`, `marketdata/web/PriceResponse.java`) or the exchange
  mapping in `infra/wiremock/mappings/`. Use the production record when the service already has it on
  its classpath (order stubs use `ExchangeAck` / `ExchangeOrderRequest`); otherwise a test-side record
  in `harness/stubs/` with fields named exactly like the provider record, so a provider change shows up
  as a fixture change.
- Use `urlPathEqualTo` + exact query/body matchers. Avoid `anyUrl()`, so a wrong request becomes an
  unmatched request and fails the test.

## Test-method rules (hard rules)

1. **No logic in tests.** Not allowed: `if/else`, ternary `?:`, `switch`, `for/while/do`, `try/catch`
   (use `assertThatThrownBy`), `&&`, `||`, `!`, `==`, `!=`, `.forEach`, `.filter`, `.ifPresent*`, `.orElseGet`.
   (Control statements are banned in every module; operators and callbacks in the backend services.)
2. **One scenario per test**: setup → action → assert. The name says the scenario and the outcome:
   `withdrawMoreThanBalanceReturns422WithError()`.
3. **Parameterize wherever cases differ only in data**: `@ParameterizedTest(name = "{0} missing -> 400")`
   with `@CsvSource` / `@MethodSource` (`Stream.of(Arguments.of(...))`). Every row is one scenario and
   uses the same assertions. Different assertion shapes → separate tests.
4. **Assert exactly**: the status code, the **full** response body (`as(Dto.class)` + `assertSoftly`,
   never a JSON string or path), the persisted state (repository/SQL), side effects (Kafka record fields via
   `KafkaProbe`, `WireMock.verify(...)` of the outbound request with its body), and the error body for
   failures (`status`, `error`, `message`).
   **No weak or non-deterministic assertions** (every test of every module, e2e included; plumbing
   packages excluded). Banned:
   - partial matches: `contains*`, `doesNotContain*`, `startsWith`/`endsWith`, `matches`,
     `anyMatch`/`allMatch`/`anySatisfy`/`allSatisfy`
   - ranges: `isGreaterThan`/`isLessThan`/`isBetween`/`isCloseTo`, `isPositive`, `isAfter`/`isBefore`,
     `hasSizeGreaterThan`
   - presence-only: `isNotNull`, `isNotEmpty`, `isNotBlank`, `isNotEqualTo`, `isIn`
   - the Hamcrest equivalents in REST-Assured `body(...)` (`containsString`, `greaterThan`, `notNullValue`,
     `hasItem`, `not`)
   - WireMock `containing`/`matching` in `verify`
   - workarounds the static checks miss: comparing `toString()` substrings, asserting `.size()` instead
     of the content

   Use the exact forms instead:
   - `isEqualTo`, `isEqualByComparingTo` (BigDecimal), `containsExactly`, `containsExactlyInAnyOrder`,
     `hasSize`, `isEmpty`, `equalToJson`
   - `usingRecursiveComparison().ignoringFields(...)` **only** for server-generated values (ids, server
     timestamps), which must then be asserted exactly another way (e.g. the returned id equals the
     persisted row's id).

   **Make the value deterministic instead of weakening the assertion** (this list is the one every
   other doc and agent refers to):
   - **Lists:** give each test its own owner/JWT subject (`TestJwt.token("history-alice")`), so an
     owner-scoped list holds exactly that test's rows. Reset global tables to their seed in the harness
     (auth: `resetToSeededUsers`).
   - **Tokens:** decode and assert the claims (`TestJwt.claims(token)` → `TokenClaims(subject, lifetime)`),
     not `isNotBlank`.
   - **Time:** fix the clock (`MutableClock`, `app.clock.fixed-instant`) and assert the exact instant.
   - **Server-generated ids/timestamps:** `ignoringFields` plus an exact check of that field elsewhere
     (the id against the persisted row).
   - **"Nothing happened":** assert an exact empty result (`isEmpty()`) after a positive signal proves
     processing finished (e.g. the resting event, a sentinel), not after an arbitrary wait.
5. **Deterministic**:
   - Time comes from `MutableClock` at a fixed `Instant`. Market-hours cases use explicit boundary instants.
   - Data is unique per test (usernames / ids / symbols derived from the test name or a fixed UUID).
     Never depend on another test's data or on execution order.
   - Async work uses `Awaitility` with an explicit `atMost`. No `Thread.sleep` / `TimeUnit.sleep`, no
     `Math.random`, no `UUID.randomUUID()` in assertions, no `Instant.now()` except to mint JWTs.
   - Kafka: go through `KafkaProbe` (acked sends; group-less snapshot reads, so probes never share
     consumer-group state) and assert by record key.
   - Timeout cases: a stubbed delay **≥ 3× the short test read timeout**, never near it (see
     [Stub fixtures](#stub-fixtures)).
6. **No magic values.** Every string and number a test uses is named, in **all** test code (every
   module, e2e included; `src/main` and `harness/` plumbing excluded). A literal may appear only:
   - in a `final` field (a constant), at any depth, e.g. `Set.of("a", "b")`
   - as the direct initializer of a local variable (`int quantity = 10;`, concatenation allowed)
   - inside an annotation (data tables such as `@CsvSource`, `@ValueSource`, `@ParameterizedTest(name)`)
   - as `0`, `1` or `""`
   - as prose that is never compared: the first argument of `Allure.step` / `as` / `describedAs` /
     `withFailMessage` / `overridingErrorMessage`, or the message of a new `AssertionError` /
     `IllegalStateException` / `IllegalArgumentException`

   Status codes, quantities, prices, symbols, paths and expected messages all need a
   name. Prefer existing types (`HttpStatus.CREATED.value()`, enums, `TradingSymbols`, `TestUsers`,
   `Topics`). Names must be **semantic** (`ORDER_QUANTITY`, `INSUFFICIENT_FUNDS_MESSAGE`), never
   value-echoes (`TEN = 10`, `S_201`; no tool can judge this, the reviewer must). Values and DTO
   factories shared across classes go into `harness/` (e.g. `Orders.market(…)`); values shared across
   modules live in `testing/test-support`. Both are plumbing and exempt.

7. **DTOs only: never build or read JSON by hand** (all test code, harness and the e2e framework included).
   - **Send objects:** REST-Assured `.body(dto)`, Kafka/WS/WireMock payloads through `Json.write(dto)`.
     The harness configures REST-Assured to serialize with the project's `Json`.
   - **Assert on objects:** deserialize every response, error body, Kafka record and STOMP frame with
     `Json.read` / `.as(Dto.class)` / `asList`, and assert on the object (fields, `isEqualTo(expectedDto)`).
     Never compare JSON strings. Never use JsonPath / `JsonNode` / `readTree`, REST-Assured
     `body("path", matcher)`, raw `body(String)`, or WireMock `matchingJsonPath`.
   - **Where DTOs come from:**
     - the service's own records (request/response/event)
     - for another service's contract (stubs), notification frames and error bodies: the
       `testing/test-support` `contracts.*` records when shared, otherwise test-side records in
       `harness/` mirroring that contract (e.g. `harness/stubs/HoldingResponse`, `harness/ApiError`)
   - **Only exception: `@RawJsonPayload(reason = "…")`** (`com.example.demo.testing.RawJsonPayload`).
     Put it on the test method (or a harness/framework raw sender such as `KafkaProbe.sendRaw` or the
     e2e `rawBody`) when the scenario needs a payload no DTO can express: a wrong JSON type, missing vs
     `null`, syntactically broken JSON, a malformed Kafka/WS message. Its data tables count as inside
     it. Never use it on a positive scenario. The `reason` names the payload a DTO cannot express. The
     annotation may sit only on test methods and in plumbing packages.
   - ArchUnit sees a call made inside a lambda as coming from the lambda, not the annotated method, so
     make raw sends outside lambdas.
   - In `/it-gen` a violation is a **hard cap of 0** ([rubric.md](rubric.md)).
8. **Mock only external communication** (per-service ITs): see [Scope](#scope-of-a-per-service-it).
   Postgres/Kafka/Redis are real containers; never mock internal beans.
9. **Never expect HTTP 500**: see [HTTP 500 is never expected](#http-500-is-never-expected).

## Enforced by

The one place that maps rules to tools. Each check reads the code at a different level (Checkstyle:
source form; PMD: syntax tree; ArchUnit: compiled bytecode and calls), so each catches what the others
cannot. None of them can tell whether an expected value is *correct*: that is the reviewer's job.

| Rule | PMD (`build/pmd/`, fails `verify`) | ArchUnit (`testing/test-rules`, Surefire) | Elsewhere |
|---|---|---|---|
| 1 No logic | `test-conventions.xml` `NoControlStatementsInTests` (every module); `it-conventions.xml` `NoLogicOperatorsInTests`, `NoCallbackBranchingInTests` (the services) | | |
| 2 / 3 One scenario, parameterize | | | reviewer (rubric C5, blocking) |
| 4 Exact values | | `TestAssertionRules.testsAssertExactValues` (every module incl. e2e; plumbing excluded) | reviewer: workarounds, `ignoringFields` |
| 5 Deterministic | | `TestAssertionRules.testsNeverSleep` | reviewer (rubric C3); 3 reruns in `/it-gen` |
| 6 No magic values | `no-magic-values.xml` `NoMagicValuesInTests` (every module). ArchUnit can't: constants are inlined into the bytecode | | reviewer: value-echo names |
| 7 DTOs only | `no-raw-json.xml` `NoRawJsonOutsideRawJsonPayload` (JSON-looking literals) | `TestAssertionRules.testsNeverReadRawJson`, `rawPayloadsOnlyFromAnnotatedMethods`, `rawJsonPayloadOnlyOnTestsOrPlumbing` | e2e `ApiRequest.Builder.body(Object)` rejects a `String` (use `rawBody`, annotated) |
| 8 Mock only external | | `IntegrationTestRules.integrationTestsMockOnlyExternalCommunication` (services) | reviewer (rubric C4) |
| `*IT` in the `it` package | | `IntegrationTestRules.integrationTestsLiveInTheItPackage` (services) | |
| 9 No HTTP 500 | `no-internal-server-error.xml` `NoInternalServerError` (services and `common`, production **and** test code) | `ServiceArchitectureRules.neverProduceInternalServerError` (production bytecode) | LogScan `unhandledHttp500` at runtime |
| Production layering | | `ServiceArchitectureRules`: `controllersLiveInWeb`, `repositoriesAreInterfacesInDomain`, `noFieldInjection`, `noStandardStreams`, `noLegacyDate` | |
| Import hygiene, one top-level class per file | | | Checkstyle `build/checkstyle/checkstyle.xml` |
| Unexpected ERROR/WARN/exception in a green run | | | LogScan (see CLAUDE.md, "When a check or test fails") |

Where they run: each module has `<Module>TestArchitectureTest` (tests: `TestAssertionRules` +
`IntegrationTestRules` in the services; `TestAssertionRules` only in `common`, `integration-tests`,
`wiremock-ws-spike`) and `<Svc>ArchitectureTest` (production: `ServiceArchitectureRules` + extras).
e2e's `E2eArchitectureTest` includes `TestAssertionRules` plus its own framework rules. The fast
`/it-gen` pre-check runs PMD, Checkstyle and all `*ArchitectureTest`s without Docker
([SKILL.md, "Running"](SKILL.md#running)).

## HTTP 500 is never expected

A 500 means an unforeseen bug in the service. No test may expect one, and no code may produce one on
purpose (PMD flags `statusCode(500)`, `INTERNAL_SERVER_ERROR`, `internalServerError()`,
`@ResponseStatus(INTERNAL_SERVER_ERROR)`; see [Enforced by](#enforced-by)).

Every outbound client uses `common.http.HttpClients.withTimeouts(...)` (explicit connect / read timeouts
from a validated `*Properties` record, so a hung dependency becomes a 503, not a hung request) and
translates failures with `common.http.Downstream`. `common.security.ApiErrorHandler` is the safety net.
Map outcomes instead:

| Situation | Status | Where |
|---|---|---|
| dependency unreachable, refusing connections, or slower than its read timeout (outage) | 503 `<Dependency> unavailable` | `common.http.HttpClients.withTimeouts` + `Downstream.failure` |
| dependency answers an error status, or an empty / unreadable body | 502 `<Dependency> returned an invalid response` | `Downstream.failure` / `invalidResponse` |
| database unreachable | 503 `The database is unavailable.` | `common.security.ApiErrorHandler` |
| unique-constraint or optimistic-lock race | 409 | `ApiErrorHandler` |
| bad input (body, params, path) | 400 `Invalid request content.` | `ApiErrorHandler` |

WireMock stubs in `harness/stubs` may simulate a *dependency* answering 500 (`serverError()`). That is
the scenario, and the service must answer 502. If a scenario actually produces a 500 at runtime
(LogScan `unhandledHttp500: true`, or the test sees a 500), that is a **product defect**: report it
through the decision gate. Never assert the 500.

## When a check or test fails

Follow CLAUDE.md, "When a check or test fails: report it, never hide it": it owns the rule, the
report format and the list of things never done without the user's decision. In `/it-gen`, "a defect
in the test code itself" means test code written **in this run** (e.g. logic you put into a test that
belongs in `harness/`); list every such fix in your report (what was wrong, what you changed). The
orchestrator takes everything else to the user (SKILL.md step 4a).

## Running

The commands (one-time install, the fast pre-check, `verify` of the in-scope ITs, LogScan) are in
[SKILL.md, "Running"](SKILL.md#running).
