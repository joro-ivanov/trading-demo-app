# portfolio-service

Demo portfolio and funds service for the testing showcase.

It is responsible for:

- cash balance
- positions
- average price and unrealized P&L
- persisted recent fills

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- DB-backed state updates
- projection-style rebuilding from events
- owner-scoped reads
- cash effects of trading

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are intended to be **production-grade examples** of persistence-heavy
  integration testing and end-to-end verification.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
