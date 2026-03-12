# Conductor UI v2

The open-source React UI for [Conductor](https://github.com/conductor-oss/conductor). It ships as both a **standalone web application** and an **npm library** that enterprise packages can extend via a plugin system.

## Running locally

### Prerequisites

- Node.js 22+
- [pnpm](https://pnpm.io/) 10.32.0 (`corepack use pnpm@10.32.0`)
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

| Script                | Description                     |
| --------------------- | ------------------------------- |
| `pnpm dev`            | Start dev server with HMR       |
| `pnpm build`          | Build standalone app to `dist/` |
| `pnpm build:lib`      | Build npm library to `dist/`    |
| `pnpm build:all`      | Build both app and library      |
| `pnpm lint`           | Run ESLint                      |
| `pnpm lint:fix`       | Run ESLint with auto-fix        |
| `pnpm prettier:check` | Check formatting                |
| `pnpm prettier:write` | Auto-format all files           |
| `pnpm typecheck`      | Type-check without emitting     |
| `pnpm test`           | Run unit tests                  |
| `pnpm test:watch`     | Run tests in watch mode         |
| `pnpm test:coverage`  | Run tests with coverage report  |

## Using as an npm library

Install the package:

```bash
npm install conductor-ui
```

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
