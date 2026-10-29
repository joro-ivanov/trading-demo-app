# integration-tests

This module contains the cross-service backend integration layer.

It is the flagship non-browser integration suite in the repository. These tests start multiple
services together and assert the real chain across boundaries, for example:

- order placement
- matching/fill publication
- portfolio updates
- notification delivery

Plumbing: `com.example.demo.integration.harness.Stack` launches the services as subprocesses from their
boot jars; containers, `KafkaProbe`, `TestJwt`, `StompClient` and JSON come from `testing/test-support`.

## Quality Boundary

- The backend services being orchestrated are **demo application code**.
- The tests in this module are intended to be **production-grade examples** of cross-service
  integration testing.

For the overall suite map, see [`../docs/test-coverage.md`](../docs/test-coverage.md).
