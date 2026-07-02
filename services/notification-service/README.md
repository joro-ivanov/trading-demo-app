# notification-service

Demo notification gateway for the testing showcase.

It provides:

- STOMP-over-SockJS browser connectivity
- price topic delivery
- user-scoped fill delivery

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- authenticated WebSocket connects
- real-time fan-out
- public vs user-scoped subscriptions

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The tests around it are the **production-grade part of the repository**.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
