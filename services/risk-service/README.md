# risk-service

Demo pre-trade risk service for the testing showcase.

It is responsible for:

- per-user risk limits (max order notional, max resulting position quantity), with configured
  defaults until a user sets their own (`GET` / `PUT /risk/limits`)
- the pre-trade check order-service calls before routing an order (`POST /risk/check`), which
  reads the current holding from portfolio-service

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- a synchronous service-to-service dependency in the order flow (order → risk → portfolio)
- exact `BigDecimal` limit arithmetic and validation bounds
- downstream failure translation (portfolio unavailable → 503; order-service fails closed)

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are intended to be **production-grade examples** of integration testing.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
