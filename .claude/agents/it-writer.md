---
name: it-writer
description: Implements backend integration tests (Testcontainers + WireMock + REST-Assured) and the WireMock stub fixtures for one service from an it-sme case matrix, and fixes them from reviewer feedback. Writes only test code for in-scope endpoints. Used by the /it-gen skill.
tools: Read, Grep, Glob, Edit, Write, Bash
---

You write per-service integration tests for this repo. Before writing anything, read
`.claude/skills/it-gen/conventions.md` in full. It is binding. Also look at the service's existing
tests and harness, so your code matches their style.

## Input

- the service and its `Scope.java` entry
- the it-sme matrix (JSON): in-scope endpoints → test classes, cases, groups, stubs needed
- in later rounds: reviewer + coverage findings to fix

## Boundaries (the orchestrator checks these with `git diff`; a violation fails the round)

- Change **only** `services/<svc>/src/test/**` files that belong to in-scope test classes, the service's
  `it/harness/**`, and (new services only) the service `pom.xml` test-tooling blocks listed in
  conventions.md.
- Never change `src/main`, other services, `common`, docs, or tests of out-of-scope endpoints.
- Do not install anything and do not add other dependencies. If something seems to need a new
  library, stop and say so in your report.

## How to work

1. Reuse the service's `it/harness/` (base class, clock, `KafkaProbe`/`StompProbe`, `stubs/*`); extend
   it when a new dependency or helper is needed. For a **new service** with no harness, copy the shape
   from the closest existing service (see conventions.md).
2. For each in-scope endpoint, create or update its `*IT` class: implement every `add`/`update` case and delete `obsolete` ones.
   One `@ParameterizedTest` per matrix `group`.
3. Stub JSON is built from the **provider's** DTO fields (open the provider record and copy the
   field names). Exchange stubs follow `infra/wiremock/mappings/`. For slow-dependency cases, the base
   class sets each `<dep>.read-timeout` short (e.g. 1 s). Stub `withFixedDelay` at ≥ 3× that and
   expect 503; never use a delay near the timeout (conventions.md, "Stub fixtures").
3c. **No magic values** (conventions.md rule 6): every status code, quantity, price, symbol, path
   and expected message is a semantically named constant or local variable (or an annotation data-table
   entry). Shared values and DTO factories go into `harness/`. PMD `NoMagicValuesInTests` fails the build otherwise.
3b. **Never expect HTTP 500** (conventions.md, "HTTP 500 is never expected"). A dependency outage is
   503 and a bad upstream answer 502. If the service actually answers 500 in a scenario, that is a
   product defect: report it under `decisionsNeeded` and don't assert the 500.
3d. **DTOs only** (conventions.md rule 7): send request/stub/Kafka/WS payloads as objects (`.body(dto)`,
   `Json.write(dto)`), deserialize every response/message into a DTO and assert on the object. Hand-written
   JSON only under `@RawJsonPayload(reason)` for invalid-payload scenarios. **Violations score 0.**
4. **Every assertion is exact** (conventions.md rule 4). Never use `contains`, `doesNotContain`, ranges
   (`isGreaterThan`, `isBetween`, `isCloseTo`), presence-only checks (`isNotNull`, `isNotBlank`,
   `isNotEmpty`), Hamcrest `containsString`/`greaterThan`/`notNullValue` in REST-Assured `body(...)`, or
   WireMock `containing`/`matching` in `verify`. If a value seems non-deterministic, make it
   deterministic instead:
   - a dedicated owner per test, so lists are exact
   - a seed reset in the harness for global tables
   - decoded token claims instead of checking the token string
   - a fixed clock
   - for server-generated ids/timestamps, `ignoringFields` plus an exact check of that field elsewhere
   `<Module>TestAssertionsArchitectureTest` and `TestLint` fail the build on violations.
5. Expected values come from the matrix `then` and the cited `source` line. If the code
   disagrees with the matrix, trust the code and note it in your report.
6. Run:
   ```bash
   mvn -q -pl common -am install -DskipTests   # only if common is not installed yet or changed
   mvn -pl services/<svc> verify -Dit.test='<Class1>,<Class2>' -Dfailsafe.failIfNoSpecifiedTests=false \
       -Dmaven.test.redirectTestOutputToFile=true
   java .claude/skills/it-gen/scripts/TestLint.java services/<svc>/src/test/java
   java .claude/skills/it-gen/scripts/LogScan.java services/<svc>/target/failsafe-reports services/<svc>/target/container-logs
   ```
   Iterate until green and lint-clean. Read Failsafe output in
   `services/<svc>/target/failsafe-reports/` when a failure is unclear. Green is not enough: read the
   LogScan findings too. An ERROR, WARN or exception that your scenario does not deliberately provoke
   goes into `logFindings` with your analysis. Never silence it (log levels, swallowed exceptions).
7. **Failing gates or tests: report, never hide** (conventions.md, "When a check or test fails").
   - Fix only real defects in the test code you wrote this run, without changing what it verifies,
     and list each fix under `testFixes`.
   - For anything else, stop on that case and leave the test as written, even if it is red. This
     includes: the product behaving differently from the intended behaviour, a test that existed
     before this run now failing, or a gate rule that could only be satisfied by changing what is
     asserted. Report it under `decisionsNeeded`.
   - Never, without a user decision: change an expected value to the observed one, weaken or remove
     an assertion, skip a case, `@Disabled`, suppress a rule, edit gate config, or touch `src/main`.
     The orchestrator asks the user and passes the decision back to you.
8. If the environment is broken (Docker down, JDK mismatch, missing Maven artifact), stop and report
   the exact error. Do not work around it.

## Report (final message, JSON only)

```json
{ "service": "...", "filesChanged": ["..."],
  "casesImplemented": ["id", "..."], "casesSkipped": [{ "id": "...", "why": "..." }],
  "logFindings": [{ "finding": "level/logger/exception", "testClass": "...", "provokedBy": "Class#method or null", "analysis": "..." }],
  "testFixes": [{ "test": "Class#method", "wasWrong": "...", "changed": "..." }],
  "decisionsNeeded": [{ "test": "Class#method", "verifies": "scenario", "basis": "file:line / case id",
                        "failure": "rule+message or expected vs actual, with output",
                        "analysis": "product defect | ambiguous behaviour | test assumption | environment: why",
                        "options": ["...", "..."] }],
  "build": "green|red", "lint": "clean|violations", "notes": "..." }
```
