# Conductor UI v2

The open-source React UI for [Conductor](https://github.com/conductor-oss/conductor). It ships as both a **standalone web application** and an **npm library** that enterprise packages can extend via a plugin system.

## Running locally

### Prerequisites

- Node.js 18+
- [pnpm](https://pnpm.io/) 10.x — we use pnpm 10 (not v11) since pnpm v11 requires Node.js v22+. The exact version is pinned via `packageManager` in `package.json`. Run once to activate it:
  ```bash
  corepack enable
  ```
- A running Conductor server (default: `http://localhost:8080`)

### Setup

```bash
pnpm install
```

Configure the backend URL in `.env` (see `.env` for defaults):

```bash
VITE_WF_SERVER=http://localhost:8080
```

### Start the dev server

```bash
pnpm dev
```

The app will be available at `http://localhost:1234`.

### Runtime configuration

The app reads runtime config from `public/context.js`, which is loaded at startup (not bundled). Copy the example and edit as needed:

```bash
cp public/context.js.example public/context.js
```

This file sets feature flags (`window.conductor`) and auth config (`window.authConfig`) without requiring a rebuild.

## Available scripts

| Script                                       | Description                                        |
| -------------------------------------------- | -------------------------------------------------- |
| `pnpm dev`                                   | Start dev server with HMR                          |
| `pnpm build`                                 | Build standalone app to `dist/`                    |
| `pnpm build:lib`                             | Build npm library to `dist/`                       |
| `pnpm build:all`                             | Build both app and library                         |
| `pnpm lint`                                  | Run ESLint                                         |
| `pnpm lint:fix`                              | Run ESLint with auto-fix                           |
| `pnpm prettier:check`                        | Check formatting                                   |
| `pnpm prettier:write`                        | Auto-format all files                              |
| `pnpm typecheck`                             | Type-check without emitting                        |
| `pnpm test`                                  | Run Vitest unit tests (single pass)                |
| `pnpm test:watch`                            | Run Vitest in watch mode                           |
| `pnpm test:coverage`                         | Run Vitest with v8 coverage report                 |
| `pnpm test:e2e`                              | Run Playwright UI tests (mocked backend, headless) |
| `pnpm test:e2e:ui`                           | Open the Playwright interactive UI                 |
| `pnpm test:e2e:headed`                       | Run UI tests in a visible browser                  |
| `pnpm test:e2e:debug`                        | Step through UI tests in the Playwright debugger   |
| `pnpm test:e2e:integration`                  | Integration E2E in Docker (Linux Chromium)         |
| `pnpm test:e2e:integration:update-snapshots` | Regenerate integration screenshot baselines        |
| `pnpm test:e2e:integration:ui`               | Host Playwright UI (debug only; not for baselines) |
| `pnpm test:e2e:integration:headed`           | Host headed Chromium (debug only)                  |

## Testing

### Unit tests (Vitest)

Tests live alongside source files as `*.test.{ts,tsx}` and run in jsdom.
They cover utilities, state machines, and component logic without a browser or server.

```bash
pnpm test            # single run
pnpm test:watch      # re-runs on file change
pnpm test:coverage   # produces coverage/index.html
```

### E2E tests (Playwright)

E2E tests live in `e2e/` and are run by Playwright against a real Chromium
browser. Every test mocks the Conductor backend with `page.route()`, so **no
running Conductor server is required** — the suite works entirely against the
built-in Vite dev server.

#### First-time setup

Install the Playwright browser binaries (one-time per machine):

```bash
pnpm exec playwright install --with-deps chromium
```

#### Running locally

```bash
# Headless (fastest) — reuses a running dev server on :1234 if one exists
pnpm test:e2e

# Interactive Playwright UI — best for writing and debugging tests
pnpm test:e2e:ui

# Watch the browser run the tests
pnpm test:e2e:headed

# Step through a single test with the Playwright debugger
pnpm test:e2e:debug

# Run one file
pnpm test:e2e e2e/smoke.spec.ts

# Run tests whose name matches a pattern
pnpm test:e2e --grep "navigates to"
```

If `pnpm dev` is already running on port 1234, Playwright reuses that server.
If nothing is running, it starts a dev server automatically for the test run.

#### Running in CI

Set `CI=true` (GitHub Actions does this automatically) and run:

```bash
pnpm exec playwright install --with-deps chromium
pnpm test:e2e
```

With `CI=true` the config:

- Always starts a fresh dev server (never reuses an existing one)
- Retries each failing test up to 2 times before marking it failed
- Uses a single worker to avoid resource contention

Example GitHub Actions job:

```yaml
- name: Install Playwright browsers
  run: pnpm exec playwright install --with-deps chromium

- name: Run E2E tests
  run: pnpm test:e2e

- name: Upload Playwright report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: playwright-report
    path: playwright-report/
    retention-days: 7
```

### Integration tests (Playwright + live backend)

Integration tests live in `e2e/integration/` and use
`playwright.integration.config.ts`. They talk to a real Conductor server and
verify the full stack end-to-end: the API client creates test data, the
browser navigates through the UI, and assertions confirm the data is rendered
correctly.

**Default runs use Docker for both the Conductor backend and Playwright
(Linux Chromium)** so screenshot baselines are identical on developer machines
and in CI — the same approach as `pnpm test:e2e:snapshots`.

#### How it works (`pnpm test:e2e:integration`)

1. [`scripts/run-integration-e2e.sh`](scripts/run-integration-e2e.sh) ensures
   the `conductor:server` image exists (builds it if missing) and that
   `dist/` is present (`pnpm build` if needed).
2. [`docker-compose.integration.yml`](docker-compose.integration.yml) starts
   Postgres + Conductor, serves the production UI with `vite preview`, then
   runs Playwright inside `mcr.microsoft.com/playwright` (shared network with
   the preview container).
3. Each test file uses `e2e/integration/api-client.ts` to create isolated test
   data (unique names per run) and cleans up in `afterAll`.
4. The compose project (`conductor-ui-e2e-integration`) is torn down when the
   script exits.

#### Running integration tests locally

**Prerequisites:** Docker must be running.

```bash
pnpm test:e2e:integration
```

This single command:

1. Builds `conductor:server` if missing (first run ~5–10 min; later ~30s via
   layer cache)
2. Builds the UI (`pnpm build`) when `dist/` is missing
3. Starts Postgres + Conductor + vite preview + Playwright via
   `docker-compose.integration.yml`
4. Tears the stack down when finished

**Visual baselines** (always update via Docker Chromium):

```bash
pnpm test:e2e:integration:update-snapshots
# or a single file:
pnpm test:e2e:integration:update-snapshots e2e/integration/workflows.spec.ts
```

**Common options**

```bash
# Run a single spec file
pnpm test:e2e:integration e2e/integration/workflows.spec.ts

# Run tests whose name matches a pattern
pnpm test:e2e:integration --grep "appears in the"

# Host-only debugging (local Chromium — do NOT use to refresh baselines)
pnpm test:e2e:integration:ui
pnpm test:e2e:integration:headed
```

`:ui` / `:headed` still use the host Playwright +
`docker/docker-compose-ui-e2e.yaml` path (global-setup). Prefer them for
stepping through failures; always regenerate screenshots with
`test:e2e:integration:update-snapshots`.

To stop a leftover integration stack manually:

```bash
docker compose -p conductor-ui-e2e-integration -f docker-compose.integration.yml down
```

#### Running integration tests in CI

Build `conductor:server` and the UI on the runner, then run the Dockerized
Playwright suite (Chromium comes from the Playwright image — no host browser
install):

```yaml
- name: Build UI
  run: pnpm build
  env:
    E2E_COVERAGE: "true"
    NODE_OPTIONS: --max-old-space-size=8192

- name: Run integration tests
  run: pnpm test:e2e:integration
  env:
    E2E_COVERAGE: "true"
    SKIP_WEBSERVER_BUILD: "true" # reuse dist/ from the build step
    OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}

- name: Upload integration report
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: playwright-integration-report
    path: playwright-integration-report/
    retention-days: 7
```

If you cache the Docker image between CI runs (e.g. using GitHub Actions
`docker/build-push-action` with `cache-to`/`cache-from`), the server build
step drops from ~10 minutes to ~30 seconds on cache hits.

## Using as a library

Install directly from a tagged release of this repository. The `&path:/ui-next`
argument tells the package manager to use the `ui-next/` subdirectory as the
package root:

```bash
# pnpm (recommended)
pnpm add "conductor-oss/conductor#<tag>&path:/ui-next"

# npm / yarn
npm install "conductor-oss/conductor#<tag>&path:/ui-next"
```

Or pin the version in `package.json`:

```json
"conductor-ui": "conductor-oss/conductor#v1.0.0&path:/ui-next"
```

Replace `<tag>` / `v1.0.0` with the release tag you want to consume
(e.g. `v3.2.1`). Available tags:
https://github.com/conductor-oss/conductor/releases

Import styles in your app entry point:

```tsx
import "conductor-ui/styles.css"; // component styles
import "conductor-ui/global.css"; // global body/font styles (optional)
```

### Extending with plugins

The plugin system lets you register additional routes, sidebar items, task forms, auth providers, and more without modifying the core package.

```tsx
import { pluginRegistry, App } from "conductor-ui";

// Register a custom sidebar item
pluginRegistry.registerSidebarItem({
  position: { target: "root", after: "definitionsSubMenu" },
  item: {
    id: "myFeature",
    title: "My Feature",
    icon: <MyIcon />,
    linkTo: "/my-feature",
    shortcuts: [],
    hidden: false,
    position: 350,
  },
});

// Register a custom route
pluginRegistry.registerRoutes([
  {
    path: "/my-feature",
    element: <MyFeaturePage />,
  },
]);

// Render the app
function Root() {
  return <App />;
}
```

### Plugin extension points

| Extension       | Method                         | Description                                        |
| --------------- | ------------------------------ | -------------------------------------------------- |
| Routes          | `registerRoutes(routes)`       | Add authenticated routes                           |
| Public routes   | `registerPublicRoutes(routes)` | Add unauthenticated routes                         |
| Sidebar items   | `registerSidebarItem(reg)`     | Inject items into the sidebar                      |
| Task forms      | `registerTaskForm(reg)`        | Custom forms for task types in the workflow editor |
| Task menu items | `registerTaskMenuItem(reg)`    | Add task types to the "Add Task" menu              |
| Auth provider   | `registerAuthProvider(reg)`    | Replace the auth implementation                    |
| Search provider | `registerSearchProvider(reg)`  | Add results to global search                       |

### Sidebar item positioning

Sidebar items use numeric positions so plugins can inject between core items without collisions. The core OSS positions are exported for reference:

```tsx
import { CORE_SIDEBAR_POSITIONS } from "conductor-ui";

// CORE_SIDEBAR_POSITIONS.ROOT:
//   executionsSubMenu: 100
//   runWorkflow:       200
//   definitionsSubMenu:300
//   helpMenu:          400
//   swaggerItem:       500

pluginRegistry.registerSidebarItem({
  position: { target: "root" },
  item: {
    id: "myItem",
    position: 350, // between definitionsSubMenu (300) and helpMenu (400)
    // ...
  },
});
```

## Project structure

```
src/
├── components/       # Shared UI components
│   └── Sidebar/      # Sidebar with plugin-injectable menu
├── pages/            # Route-level page components
├── plugins/          # Plugin registry and fetch utilities
├── shared/           # Auth state machine and context
├── theme/            # MUI theme provider
├── types/            # Shared TypeScript types
└── utils/            # Feature flags, constants, helpers
public/
├── context.js        # Runtime config (gitignored, not bundled)
└── context.js.example
```

## Peer dependencies

When consuming as a library, the following must be provided by the host app:

- `react` ^18
- `react-dom` ^18
- `react-router` / `react-router-dom` ^7
- `@mui/material`, `@mui/icons-material`, `@mui/system`, `@mui/x-date-pickers`
- `@emotion/react`, `@emotion/styled`
