# alert-service

Demo price-alert service for the testing showcase.

It is responsible for:

- creating, listing, reading and deleting a user's price alerts (`/alerts`, owner-scoped by the JWT)
- validating the alert's symbol against market-data-service (unknown → 422, unavailable → 503)
- consuming `price.ticks` and triggering each matching `ACTIVE` alert exactly once
  (`ABOVE`: price ≥ threshold, `BELOW`: price ≤ threshold)
- publishing `alerts.triggered`, which notification-service pushes to the owner over WebSocket
  (`/user/queue/alerts`) — clients never poll for triggered alerts

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- REST validation, ownership and state rules (a triggered alert cannot be deleted)
- an outbound HTTP dependency with explicit failure translation (404 → 422, 5xx/timeout → 503)
- an idempotent Kafka consumer and an after-commit Kafka producer
- time from an injected `Clock` (`app.clock.fixed-instant`)

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are intended to be **production-grade examples** of service integration
  testing (generated with `/it-gen`).

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
