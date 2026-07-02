# auth-service

Demo authentication service for the testing showcase.

It provides:

- user registration
- user login
- JWT issuance for the other services

## Purpose In This Repository

This service exists to create realistic testing scenarios around:

- validation
- duplicate-user handling
- authentication failures
- JWT-based authorization across the stack

## Quality Boundary

- The service implementation is **demo-grade backend code**.
- The unit, integration, API e2e, and UI e2e tests that exercise it are the **production-grade
  part of the repository**.

For suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).
