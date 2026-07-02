# Frontend Development

This frontend exists to support the repository's testing story.

## Quality Boundary

- The dashboard implementation is **demo-grade frontend code**.
- The unit tests, component tests, UI e2e tests, and structural checks around it are intended to be
  **production-grade examples**.

For overall suite coverage, see [`../../docs/test-coverage.md`](../../docs/test-coverage.md).

The required way to work on `frontend/web-dashboard` is the devcontainer in
[`frontend/web-dashboard/.devcontainer`](./.devcontainer). That keeps Node and the frontend toolchain
inside the container instead of on the host.

## Devcontainer

Current devcontainer configuration:

- base image: `mcr.microsoft.com/devcontainers/typescript-node:24`
- workspace folder in container: `/workspace`
- post-create step: `npm install`
- forwarded port: `5173`
- default remote user: `node`

### Open it

In VS Code:

1. Open the repository root.
2. Install the `Dev Containers` extension if it is not already installed.
3. Run `Dev Containers: Reopen in Container`.
4. When prompted for the configuration, use `frontend/web-dashboard/.devcontainer/devcontainer.json`.

After the container is created, it runs `npm install` automatically.

### Daily workflow inside the devcontainer

```bash
cd frontend/web-dashboard
npm run build
npm run lint
npm run test:unit
npm run test:component
npm run depcruise
```

Vitest note:

- unit tests run with the forked pool by default
- this is intentional because the default worker pool has shown a shutdown hang in this repo after
  successful test completion
- the stable command is still the normal one:
  `npm run test:unit`

To run the Vite dev server:

```bash
cd frontend/web-dashboard
npm run dev -- --host 0.0.0.0
```

Then open the forwarded port `5173` from the editor.

### Rebuild the frontend used by the local app

Building the frontend inside the devcontainer updates only the local Vite bundle:

```bash
cd frontend/web-dashboard
npm run build
```

If the app is being served through the repository `docker compose` stack, rebuild/restart that stack
so `http://localhost:8088` picks up the new frontend image:

```bash
docker compose up --build -d
```

If you want a full restart:

```bash
docker compose down
docker compose up --build -d
```

Do not run the frontend toolchain on the host. Use the devcontainer for all frontend build, lint,
unit-test, and component-test work.
