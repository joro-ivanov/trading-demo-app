# order-service

Demo order-management service for the testing showcase.

It is responsible for:

- placing orders
- cancelling orders
- applying market-hours and pre-trade guards
- publishing order events and reacting to fills

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- synchronous HTTP flows
- asynchronous event handling
- state transitions
- race conditions
- security and ownership rules

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are intended to be **production-grade examples** of service integration,
  cross-service integration, API e2e, and UI e2e testing.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
