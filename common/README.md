# common

This module contains shared backend support code used across the services.

It exists to support the test showcase:

- shared `Money` / numeric helpers
- JWT helpers
- shared security/resource-server configuration

## Quality Boundary

- The code in this module is **demo/support code**, not production-grade platform code.
- The **tests that exercise this module and the services that depend on it are the production-grade
  part of the repository**.

For the overall test inventory, see [`../docs/test-coverage.md`](../docs/test-coverage.md).
