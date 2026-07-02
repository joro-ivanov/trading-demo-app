# market-data-service

Demo market-data and matching service for the testing showcase.

It provides:

- synthetic/live-ish prices for the dashboard
- latest-price reads
- order matching that produces fills

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- Redis-backed reads
- event-driven matching
- price streams
- fill publication

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are the **production-grade part of the repository**.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
