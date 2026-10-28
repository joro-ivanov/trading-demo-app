# Integration-test conventions (`/it-gen`)

The single source of truth for **it-writer** (what to produce) and **it-reviewer** (what to check).
It extends `docs/testing-cookbook.md`; where they disagree, this file describes the target layout.

## Scope of a per-service IT

- Boot **one** service: `@SpringBootTest(webEnvironment = RANDOM_PORT)`.
- **Real** infrastructure via Testcontainers: Postgres (`postgres:17-alpine`), Kafka
  (`confluentinc/cp-kafka:7.8.0`), Redis (`redis:7-alpine`): the same images as `docker-compose.yml`.
- **Mock only communication with other systems**: HTTP calls to other services/the exchange with a
  WireMock `WireMockServer`, the exchange WS feed with a WireMock **WS** message stub (REST and WS stubs
  on **separate** servers), and inbound Kafka messages produced by the test itself.
- **Never** mock anything internal: no Mockito, `@MockitoBean`, `@MockBean`, `@SpyBean`, or stubbed
  repositories/services/clients. The only allowed bean override is the `@Primary` `MutableClock`.
- Drive the API over **real HTTP** with **REST-Assured** against `@LocalServerPort`. Do not use MockMvc in
  new or touched classes. If the service's `pom.xml` has no `io.rest-assured:rest-assured` test
  dependency, add it (version is managed in the root pom). That is the **only** non-test file a
  writer may change.

## Layout (one class per endpoint / consumer)

```
services/<svc>/src/test/java/com/example/demo/<pkg>/it/
  PlaceOrderIT.java              # one class per endpoint: POST /orders
  CancelOrderIT.java             # DELETE /orders/{id}
  OrderHistoryIT.java            # GET /orders/history
  OrderOutcomeConsumerIT.java    # one class per Kafka listener class
  harness/
    <Svc>ITBase.java             # containers, WireMock servers, properties, clock, REST spec, tokens
    MutableClock.java            # + TestClockConfig (@TestConfiguration, @Primary Clock)
    KafkaProbe.java              # produce / await-record helpers (polling loop lives here)
    stubs/
      ExchangeStubs.java         # one fixture class per external dependency
      PortfolioStubs.java
      MarketDataStubs.java
```

- `harness/` is excluded from PMD's control-flow ban (`build/pmd/test-conventions.xml`) and from
  `TestLint`. Plumbing that needs logic (polling, parsing, token minting) goes **there**, never into a test.
- All five services follow this layout (see `docs/test-coverage.md` for the class list). Reuse the
  existing `harness/` classes (`<Svc>ITBase`, `KafkaProbe`, `StompProbe`, `stubs/*`) and extend them
  rather than adding parallel helpers. Never touch tests of endpoints that are not in scope.
- A **new service** gets the same structure: copy the base/probe shape from the closest existing
  service and add:
  - `<Svc>IntegrationTestArchitectureTest`, generated from
    `.claude/skills/it-gen/IntegrationTestArchitectureTest.template.java` (replace `__PKG__` / `__NAME__`)
  - `<Svc>ArchitectureTest` (the new services already have one with `neverProduceInternalServerError`)
  - the container log consumers and `logback-test.xml` file appenders, for every container it starts
    (`.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("container.<name>")))`, logged to
    `target/container-logs/<name>.log`)
  Its `pom.xml` also needs the `rest-assured` test dependency, the `jacoco-maven-plugin`, and the
  `maven-pmd-plugin` block with both rulesets (copy from `services/auth-service/pom.xml`).

### `<Svc>ITBase` shape

- Singleton containers: `static final` containers and WireMock servers started in a `static {}` block
  (not `@Container`), so every IT class of the service shares them and the cached Spring context.
- `@DynamicPropertySource` wires `spring.datasource.*`, `spring.kafka.bootstrap-servers`,
  `spring.data.redis.*`, `<dep>.base-url` and `exchange.ws-url` (see the service's `application.yaml`).
  It also sets **every dependency's `<dep>.read-timeout` short** (e.g. `"1s"`), so slow-dependency
  cases run fast. Every outbound client uses `common.http.HttpClients.withTimeouts`; the real defaults
  (2–5 s) would only slow the suite down.
- `@BeforeEach`: `resetAll()` every WireMock server, apply the **default** stubs from the fixtures, and
  set the clock to a fixed instant.
- `@AfterEach`: `assertThat(server.findAllUnmatchedRequests()).isEmpty()` for every WireMock server.
- Exposes `given(token)` (a REST-Assured spec with base URI, port, JSON content type and bearer token)
  and token helpers built on `com.example.demo.common.security.Jwt` (`Jwt.issue(subject, SECRET, ttl)`;
  the secret is the service's `security.jwt.secret` test value).

### Stub fixtures

- One class per external dependency, static methods named after the **behaviour**:
  `acceptsOrder()`, `rejectsOrder()`, `holdings("AAPL", 10)`, `noHoldings("AAPL")`, `serverError()`,
  `slowResponse(Duration)`, `malformedBody()`, `emptyBody()`, `notFound()`.
- **Timeout cases are deterministic only far from the boundary:** stub `withFixedDelay` at **≥ 3× the
  test read timeout** (e.g. 3000 ms against 1 s) and expect 503 `<Dependency> unavailable`. Never use
  a delay close to the timeout (that's a race). And never a delay just under it to prove "slow but OK":
  assert that only with a delay far below the timeout.
- The JSON must match the **provider's real DTO** (e.g. `portfolio/web/HoldingResponse.java`,
  `marketdata/web/PriceResponse.java`) or the exchange mapping in `infra/wiremock/mappings/`. Build it
  from fields named exactly like the provider record, so a provider change shows up as a fixture change.
- Use `urlPathEqualTo` + exact query/body matchers. Avoid `anyUrl()`, so a wrong request becomes an
  unmatched request and fails the test.

## Test-method rules (hard rules)

1. **No logic in tests.** Not allowed: `if/else`, ternary `?:`, `switch`, `for/while/do`, `try/catch`
   (use `assertThatThrownBy`), `&&`, `||`, `!`, `==`, `!=`, `.forEach`, `.filter`, `.ifPresent*`, `.orElseGet`.
   Enforced by PMD and `TestLint.java`.
2. **One scenario per test**: setup → action → assert. The name says the scenario and the outcome:
   `withdrawMoreThanBalanceReturns422WithError()`.
3. **Parameterize wherever cases differ only in data**: `@ParameterizedTest(name = "{0} missing -> 400")`
   with `@CsvSource` / `@MethodSource` (`Stream.of(Arguments.of(...))`). Every row is one scenario and
   uses the same assertions. Different assertion shapes → separate tests.
4. **Assert exactly**: the status code, the **full** response body (`as(Dto.class)` + `assertSoftly`,
   or JSON field equality), the persisted state (repository/SQL), side effects (Kafka record fields via
   `KafkaProbe`, `WireMock.verify(...)` of the outbound request with its body), and the error body for
   failures (`status`, `error`, `message`).
   **No weak or non-deterministic assertions** (enforced by `<Svc>IntegrationTestArchitectureTest` and
   `TestLint`). Banned:
   - partial matches: `contains*`, `doesNotContain*`, `startsWith`/`endsWith`, `matches`,
     `anyMatch`/`allMatch`/`anySatisfy`/`allSatisfy`
   - ranges: `isGreaterThan`/`isLessThan`/`isBetween`/`isCloseTo`, `isPositive`, `isAfter`/`isBefore`,
     `hasSizeGreaterThan`
   - presence-only: `isNotNull`, `isNotEmpty`, `isNotBlank`, `isNotEqualTo`, `isIn`
   - the Hamcrest equivalents in REST-Assured `body(...)` (`containsString`, `greaterThan`, `notNullValue`,
     `hasItem`, `not`)
   - WireMock `containing`/`matching` in `verify`

   Use the exact forms instead:
   - `isEqualTo`, `isEqualByComparingTo` (BigDecimal), `containsExactly`, `containsExactlyInAnyOrder`,
     `hasSize`, `isEmpty`, `equalToJson`
   - `usingRecursiveComparison().ignoringFields(...)` **only** for server-generated values (ids, server
     timestamps), which must then be asserted exactly another way (e.g. the returned id equals the
     persisted row's id).

   How to make a value exact instead of weakening the assertion:
   - **Lists:** give each test its own owner/JWT subject (`tokenFor("history-alice")`), so an
     owner-scoped list holds exactly that test's rows. Reset global tables to their seed in the harness
     (auth: `resetToSeededUsers`).
   - **Tokens:** decode and assert the claims (`claimsOf(token)` → subject + lifetime), not `isNotBlank`.
   - **Time:** fix the clock (`MutableClock`, `app.clock.fixed-instant`) and assert the exact instant.
   - **"Nothing happened":** assert an exact empty result (`isEmpty()`) after a positive signal proves
     processing finished (e.g. the resting event, a sentinel), not after an arbitrary wait.
5. **Deterministic**:
   - Time comes from `MutableClock` at a fixed `Instant`. Market-hours cases use explicit boundary instants.
   - Data is unique per test (usernames / ids / symbols derived from the test name or a fixed UUID).
     Never depend on another test's data or on execution order.
   - Async work uses `Awaitility` with an explicit `atMost`. No `Thread.sleep`, no `Math.random`,
     no `UUID.randomUUID()` in assertions, no `Instant.now()` except to mint JWTs.
   - Kafka: unique consumer `group.id` per probe, assert by record key.

## HTTP 500 is never expected

A 500 means an unforeseen bug in the service. No test may expect one, and no code may produce one on
purpose. Enforced by:
- PMD `build/pmd/no-internal-server-error.xml`, on production and test code: `statusCode(500)`,
  `INTERNAL_SERVER_ERROR`, `internalServerError()`, `@ResponseStatus(INTERNAL_SERVER_ERROR)`
- ArchUnit: `<Svc>ArchitectureTest.neverProduceInternalServerError` on production bytecode, and
  `integrationTestsNeverExpectInternalServerError` on ITs
- TestLint

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

## When a check or test fails: report it, never hide it

This applies to every quality gate (Checkstyle, PMD, ArchUnit, TestLint) and every functional test
(per-service ITs, the cross-service flagship, e2e, unit). It also applies to the **logs** of those runs: an
ERROR, WARN or exception that no test scenario deliberately provokes is reported like a failure, even
when every test passed (`LogScan.java`, see SKILL.md step 2b). A failing check is information about a
possible defect, so it must reach the user. Don't make it go away.

1. **Fix it yourself only if the defect is in the test code you are writing in this run**, and the
   fix doesn't change what the test verifies. For example: it doesn't compile, an unused import, a
   misused harness helper, logic you put into a test that belongs in `harness/`, or a stub missing for
   a dependency. List every such fix in your report (what was wrong, what you changed).
2. **Everything else is a decision for the user.** Stop on that case, leave the test as written, and
   report it with:
   - **Test:** `Class#method` and the scenario it verifies.
   - **Basis:** the production code line, matrix case or doc that defines the expected behaviour.
   - **Failure:** the exact gate rule and message, or expected vs actual (status, body, state, event),
     plus the relevant output.
   - **Why it fails (your analysis):** product defect, ambiguous or undocumented behaviour, a wrong
     assumption in the test, or environment.
   - **Options:** e.g. fix the product, confirm the behaviour is intended and update the expectation,
     or track it as a known issue.

   This covers: the product behaving differently from the intended behaviour; any test that existed
   before this run now failing; and a gate rule that could only be satisfied by changing what is
   asserted.
3. **Never, without an explicit user decision:**
   - change an expected value to the observed one
   - weaken, remove or loosen an assertion
   - delete or skip a case, or `@Disabled`
   - suppress a rule: `@SuppressWarnings("PMD…")`, `// NOPMD`, `CHECKSTYLE:OFF`, ArchUnit freezing,
     ignore lists or exclusions
   - edit `build/pmd/*`, `build/checkstyle/*` or the ArchUnit rules
   - silence logs: raise levels or remove appenders in `logback-test.xml`, or catch and swallow
     exceptions in the harness or tests so they stop appearing
   - change `src/main` to make a test pass

## Running

```bash
mvn -q -pl common -am install -DskipTests                      # once, or when common changed
mvn -pl services/<svc> verify -Dit.test='PlaceOrderIT,CancelOrderIT' -Dfailsafe.failIfNoSpecifiedTests=false
java .claude/skills/it-gen/scripts/TestLint.java services/<svc>/src/test/java/<...>/it
```
`verify` also runs the service's unit/ArchUnit tests, PMD and Checkstyle.
