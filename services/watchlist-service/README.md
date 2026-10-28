# watchlist-service

Demo watchlist service for the testing showcase.

It is responsible for:

- per-user watchlists (create, list, delete)
- the symbols on a watchlist (add, remove)
- enriching a watchlist with the latest prices from `market-data-service`

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- owner-scoped CRUD with uniqueness and size limits
- validating input against another service (unknown symbol → 422, market-data down → 503)
- graceful degradation (prices omitted with `pricesAvailable: false` when market-data is down)
- an outbound HTTP dependency mocked with WireMock in per-service integration tests

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are intended to be **production-grade examples** of integration testing with
  real Postgres and a mocked external HTTP dependency.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
