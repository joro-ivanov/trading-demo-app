# Scoring rubric (used by it-reviewer)

Score **only the in-scope test classes and fixtures** listed in the request. Score 0–10 with one
decimal. Done = **score ≥ 9.0 and zero blocking issues**.

| Criterion | Max | Full marks when… |
|---|---|---|
| C1 Matrix coverage | 3.0 | Every `must` case in the matrix is implemented, most `should` cases are, and every catalogue category is covered or justified `n/a`: status codes, bodies, validation, missing data, wrong data, auth/tokens, ownership, downstream faults. |
| C2 Assertion strength | 2.0 | Exact status + full body (or every field) + error body on failures + persisted state + Kafka record fields + `WireMock.verify` of outbound requests incl. body. Every value asserted exactly; no partial/range/presence-only assertions (see conventions.md rule 4); `ignoringFields` only for server-generated values that are asserted exactly elsewhere. |
| C3 Determinism | 2.0 | Fixed `MutableClock`, unique data per test, no order dependence, Awaitility with `atMost`, no sleep/random/now (except JWT minting), unmatched WireMock requests fail, consumer group per probe. Timeout cases use a fixed delay ≥ 3× the (short) test read timeout, never a delay near it. |
| C4 Mock boundary | 1.5 | Only external communication is stubbed; real Postgres/Kafka/Redis; stub JSON matches the provider DTO field-for-field; precise URL/body matchers; fixtures reused, not inline. |
| C5 Structure & readability | 1.5 | One class per endpoint/listener, one scenario per test, parameterized where only data differs, names state scenario + outcome, every value semantically named (no magic values, no value-echo constants like `TEN = 10`), harness holds all plumbing, follows `conventions.md`. |

## Hard caps (apply the lowest)

| Condition | Cap |
|---|---|
| Build, PMD, Checkstyle or ArchUnit red (except failures the user decided to leave red) | 0 |
| Any test failed in one of the 3 reruns (flaky; except user-decided known failures) | 5 |
| Any conditional logic/operator or loop in a test (TestLint or PMD violation) | 5 |
| Any weak/non-deterministic assertion (`contains`, ranges, `isNotNull`/`isNotBlank`, Hamcrest `containsString`, WireMock `containing`, … — ArchUnit or TestLint violation) | 5 |
| Internal mocking (Mockito, `@MockitoBean`, stubbed internal bean) | 4 |
| Production code (`src/main`) changed by the writer | 0 |
| Out-of-scope test class modified | 6 |
| A test expecting HTTP 500 (PMD `NoInternalServerError`, ArchUnit, TestLint) | 4 |
| A failure hidden without a user decision (expected value switched to observed, assertion loosened or removed, case deleted, `@Disabled`, rule suppression, gate-config edit, `src/main` change) | 0 |

## Always blocking (even when the score is ≥ 9)

- A `must` case from the matrix is missing.
- Data-only duplicates that should be one `@ParameterizedTest`.
- A magic value in a test (PMD `NoMagicValuesInTests` / TestLint), or a constant whose name only echoes its
  value (`TEN = 10`, `S_201`) instead of saying what it means.
- An expected value that contradicts the code (the reviewer must cite the code line).
- A test expecting 500, or a scenario whose response was an unhandled 500 at runtime (LogScan
  `unhandledHttp500: true`). The first is a test defect; the second is a product defect for the decision gate.
- `ignoringFields(...)` on a field that is not server-generated, or a server-generated field never
  asserted exactly anywhere.
- An assertion weakened, or any other change that makes a failing test or gate pass without a user
  decision. A possible defect must reach the user through the decision gate, not be hidden.

## Output (JSON only)

```json
{
  "score": 8.4,
  "criteria": { "C1": 2.5, "C2": 1.8, "C3": 2.0, "C4": 1.2, "C5": 0.9 },
  "capsApplied": [],
  "blocking": [
    { "file": "services/order-service/src/test/java/.../PlaceOrderIT.java", "line": 88,
      "issue": "quantity 0 / -1 / missing are three copies of one test", "fix": "merge into @ParameterizedTest with @CsvSource" }
  ],
  "improvements": [ { "file": "...", "line": 0, "issue": "...", "fix": "..." } ],
  "suspectedProductBugs": [ { "case": "POST/orders-H7-02", "evidence": "..." } ],
  "hiddenFailures": [ { "file": "...", "line": 0, "change": "expected 422 -> 500 with no user decision" } ],
  "logFindings": [ { "level": "ERROR", "logger": "...", "exception": "...", "firstAppFrame": "...", "byRun": {}, "testClass": "...",
                     "classification": "expected|unexpected", "category": "scenario|product|nondeterminism|infra/noise",
                     "reason": "Class#method stubs an exchange 500, so ExchangeClient.route throwing is the scenario" } ]
}
```
