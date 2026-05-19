/**
 * Playwright integration test configuration.
 *
 * Unlike the default playwright.config.ts (which mocks all /api calls and
 * tests the UI in isolation), this config runs against a live Conductor
 * backend.  The global setup script starts the backend via Docker Compose
 * automatically; the Vite dev server is then pointed at it.
 *
 * Quick start:
 *
 *   # Build the server image once (slow — only needed when server code changes)
 *   docker build -t conductor:server -f docker/server/Dockerfile .
 *
 *   # Run all integration tests (Docker is managed automatically)
 *   pnpm test:e2e:integration
 *
 * The backend URL defaults to http://localhost:8000.  Override with:
 *   CONDUCTOR_SERVER_URL=http://my-server:8000 pnpm test:e2e:integration
 *
 * Set SKIP_DOCKER=true to skip Docker management entirely (use a server you
 * started yourself):
 *   SKIP_DOCKER=true pnpm test:e2e:integration
 */

import { defineConfig, devices } from "@playwright/test";

const CONDUCTOR_SERVER_URL =
  process.env.CONDUCTOR_SERVER_URL ?? "http://localhost:8000";

export default defineConfig({
  testDir: "./e2e/integration",

  // Integration tests modify shared state, so run serially within each file.
  // Files themselves can still run in parallel (fullyParallel: false means
  // tests within a file run serially).
  fullyParallel: false,
  workers: process.env.CI ? 1 : 2,

  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,

  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-integration-report" }],
  ],

  globalSetup: "./e2e/integration/global-setup.ts",
  globalTeardown: "./e2e/integration/global-teardown.ts",

  use: {
    baseURL: "http://localhost:1234",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    // Integration tests can be slower due to real API calls.
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  // Build the app and serve it with `vite preview`.
  // Integration tests run against the production bundle — not the dev server —
  // so they exercise the same artifact that gets deployed.
  // VITE_WF_SERVER is passed to both the build step (ignored) and the preview
  // step (picked up by preview.proxy in vite.config.ts).
  webServer: {
    command: "pnpm build && pnpm preview",
    url: "http://localhost:1234",
    reuseExistingServer: !process.env.CI,
    timeout: 300_000, // allow up to 5 min for a cold build
    env: {
      VITE_WF_SERVER: CONDUCTOR_SERVER_URL,
    },
  },
});
