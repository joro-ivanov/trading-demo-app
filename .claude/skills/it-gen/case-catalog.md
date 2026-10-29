# Case catalogue (used by it-sme)

Walk every category for every in-scope endpoint or listener. A category that does not apply gets
an explicit `n/a` with a one-line reason in the matrix. Cases that differ only in data are
grouped (`group` field) so the writer turns them into one `@ParameterizedTest`.

## HTTP endpoints

| # | Category | Cases to derive |
|---|---|---|
| H1 | Happy path | Each distinct success shape: exact status (200/201/204), **full** body, `Location` header if set, persisted rows, emitted Kafka events, outbound calls (WireMock `verify` with body). |
| H2 | Request validation | For **each** field: missing, `null`, blank, wrong type, below/above range, boundary −1/exact/+1, unknown enum value, too long. Also malformed JSON, empty body, wrong `Content-Type`, unknown/duplicate fields if the DTO rejects them. Path/query params: missing, malformed (non-UUID id), out of range. → 400 + error body.  Cases a DTO cannot express (wrong JSON type, missing vs `null`, malformed JSON) are `@RawJsonPayload(reason)` tests; all others send DTOs. |
| H3 | Missing data | Unknown id → 404; resource of another user (no existence leak → 404 or 403, whatever the code does); empty collections → 200 `[]`. |
| H4 | Wrong data / business rules | Domain rejections with their exact status (409/422/400): insufficient funds/holdings, invalid state transition (cancel a FILLED order), duplicate registration, wrong credentials. |
| H5 | Authentication | No `Authorization` header; `Bearer` with empty token; malformed token; wrong signature (`TestJwt.forged(sub)`); expired (`TestJwt.expired(sub)`); not-yet-valid; wrong scheme (`Basic …`); token for a deleted/unknown user if the service checks. → 401 + JSON error body. Public endpoints (`/auth/login`, `/auth/register`) must work **without** a token. |
| H6 | Authorization / ownership | User B reads/cancels/lists user A's resources; lists only return the caller's own rows. (No roles exist yet. If roles appear in a `SecurityFilterChain`/`@PreAuthorize`, add role × endpoint cases.) |
| H7 | Downstream faults (per external dependency) | 4xx, 5xx, timeout/slow, connection reset, malformed body, empty body, unexpected fields. Exact resulting status/body: **unreachable, refused, or slower than its read timeout → 503 "<Dependency> unavailable"** (stub `withFixedDelay` ≥ 3× the short test read timeout; see conventions.md); **error status, empty or unreadable body → 502 "<Dependency> returned an invalid response"** (common `Downstream`); an expected answer (e.g. 404 = unknown symbol) → its business status. **Never 500.** No partial persistence and no event emitted when the call fails (or the documented compensation). |
| H12 | Infrastructure outage | Only where the code handles it: database unreachable → 503 "The database is unavailable."; unique-constraint / optimistic-lock race → 409. A scenario whose honest outcome would be 500 is a **product defect**: list it under `suspectedProductBugs`, never as an expected 500. |
| H8 | Time-dependent rules | Market hours: exactly at open, 1 s before open, exactly at close, 1 s after close, weekend, holiday if modelled. Use `MutableClock`. |
| H9 | Money & numbers | Decimal precision/scale, rounding, zero, negative, very large, `Money` currency mismatch. |
| H10 | State transitions | A table of every (from-state, action) → (to-state or error). One parameterized test per outcome shape. This replaces model-based tools such as GraphWalker. |
| H11 | Idempotency / concurrency | Repeated request with the same input, double cancel, deposit then withdraw consistency (only if the code defines the behaviour). |

## Kafka listeners

| # | Category | Cases |
|---|---|---|
| K1 | Valid message | Exact resulting DB state / outbound event / WS push. |
| K2 | Malformed payload | Invalid JSON, missing required field, wrong type. The service must not crash and later valid messages are still processed.  Sent via the `@RawJsonPayload` raw sender (e.g. `KafkaProbe.sendRaw`) from an annotated test. |
| K3 | Unknown reference | e.g. a fill for an unknown order id. |
| K4 | Duplicate / out-of-order | The same fill twice; resting after filled. |
| K5 | Contract | The payload shape the **producer** actually sends (read the producer's record, e.g. `marketdata/match/FillEvent.java`). |

## WebSocket (notification, market-data feed)

| # | Category | Cases |
|---|---|---|
| W1 | Handshake auth | Valid token, missing, invalid, expired. |
| W2 | Push | Each event type reaches the right subscriber only (another user's fill is not pushed). |
| W3 | Feed faults | Malformed frame, silence/no match, disconnect.  Malformed frames via an annotated raw sender. |

## Matrix entry format

```json
{
  "id": "POST/orders-H2-03",
  "endpoint": "POST /orders",
  "category": "H2",
  "scenario": "quantity is zero",
  "group": "POST/orders-invalid-body",
  "given": "valid token; exchange stub default",
  "when": "POST /orders {symbol:AAPL, side:BUY, quantity:0}",
  "then": { "status": 400, "body": { "status": 400, "error": "Bad Request", "message": "<exact text from code>" },
            "persisted": "no order row", "events": "none", "outbound": "exchange not called" },
  "stubs": ["ExchangeStubs.acceptsOrder"],
  "priority": "must",
  "status": "add",
  "existingTest": null,
  "source": "PlaceOrderRequest.java:L12 @Positive"
}
```
`priority`: `must` (a status code, auth, validation or business rule visible in the code) or `should`
(defensive). `status`: `existing | add | update | obsolete`. `source` cites the code line that defines
the behaviour, so expected values come from the code and are not invented.

Every `then` value is **exact**: a full body, an exact list (`[]` included), an exact number, or an
exact instant from the fixed clock. Never use "contains", "not empty", "greater than" or "some token";
make the value exact the ways conventions.md rule 4 lists.
Generated ids and server timestamps are listed under `then.ignoredGenerated` together with how they
are pinned elsewhere, e.g. `"id": "equals the persisted order row id"`.
