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

- base image: `mcr.microsoft.com/devcontainers/typescript-node:24` (Node 24, same as `.nvmrc`,
  `package.json` `engines` and the production `Dockerfile`)
- workspace: the repository root, mounted at `/workspace`
- post-create step: `scripts/install-firefox.sh` installs Mozilla's official Firefox and geckodriver
  (amd64 or arm64) for the WDIO component tests, then `npm install` in `frontend/web-dashboard`
- `FIREFOX_BIN` / `GECKODRIVER_PATH` point WDIO at that browser and driver (see "Component tests" below)
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

### Component tests (WebdriverIO): browser configuration

`wdio.conf.ts` reads the browser from environment variables, so the same config runs in the
devcontainer, in a test container or on any machine:

| Variable | Effect | Unset |
|---|---|---|
| `FIREFOX_BIN` | Firefox binary to drive | WebdriverIO finds or downloads Firefox |
| `GECKODRIVER_PATH` | geckodriver to use | WebdriverIO downloads geckodriver |
| `WDIO_HEADLESS` | `false` opens a visible browser for debugging | headless |

The component tests run in **Firefox**. Mozilla publishes official Linux builds of Firefox (its APT
repository, `packages.mozilla.org`) and geckodriver (GitHub releases) for both amd64 and arm64, so
the devcontainer and `Dockerfile.test` install the same official browser on Intel and Apple Silicon.
Both use `scripts/install-firefox.sh`; pin a different geckodriver with `GECKODRIVER_VERSION`.

Locate elements with **CSS selectors** (e.g. `button[type="submit"]`). WebdriverIO's text and `aria/`
selectors compile to XPath, which Firefox's WebDriver BiDi `locateNodes` doesn't support, so every
lookup would log a warning and fall back to classic WebDriver.

Known log noise in a container run. These come from Firefox or WebdriverIO themselves, not from the
app or the tests:

- `Sandbox: CanCreateUserNamespace() clone() failure: EPERM`: Firefox can't create its process
  sandbox in an unprivileged container (the reason Chrome needed `no-sandbox`). Harmless for tests.
- `Realm.sys.mjs … TypeError: argument is not a global object`: internal to Firefox's remote agent,
  once per session.
- `Failed to resolve dependency: p-iteration, present in 'optimizeDeps.include'`: from WebdriverIO's
  own browser-runner Vite preset.
- `You are running in headless mode` / `WebDriver BiDi listening on …`: informational.

### Run all frontend checks in a throwaway container

`Dockerfile.test` (Node 24 + Mozilla Firefox and geckodriver) runs lint, typecheck, dependency rules,
Vitest and the WDIO component tests with nothing installed on the host:

```bash
cd frontend/web-dashboard
docker build -f Dockerfile.test -t web-dashboard-tests .
docker run --rm web-dashboard-tests                          # everything
docker run --rm web-dashboard-tests npm run test:component   # a single suite
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
