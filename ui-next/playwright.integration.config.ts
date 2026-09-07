/**
 * Playwright integration test configuration.
 *
 * Default path (`pnpm test:e2e:integration`) runs Playwright inside Linux
 * Chromium Docker via docker-compose.integration.yml so screenshot baselines
 * match locally and in CI.
 *
 * Host debugging (not for updating baselines):
 *   pnpm test:e2e:integration:headed
 *   pnpm test:e2e:integration:ui
 *
 * Update visual baselines (Docker):
 *   pnpm test:e2e:integration:update-snapshots
 *
 * Inside Docker, SKIP_DOCKER + SKIP_WEBSERVER are set: compose already started
 * Conductor and vite preview. On the host headed/ui path, global-setup starts
 * the ui-e2e backend and webServer runs vite preview.
 */

import { defineConfig, devices } from "@playwright/test";
import { loadIntegrationEnv } from "./e2e/integration/load-env";

// Pick up ui-next/.env.local (e.g. OPENAI_API_KEY) before tests / docker start.
loadIntegrationEnv();

const CONDUCTOR_SERVER_URL =
  process.env.CONDUCTOR_SERVER_URL ?? "http://localhost:8000";
const BASE_URL =
  process.env.BASE_URL ??
  process.env.PLAYWRIGHT_BASE_URL ??
  "http://localhost:1234";
const SKIP_WEBSERVER = process.env.SKIP_WEBSERVER === "true";

export default defineConfig({
  testDir: "./e2e/integration",

  // Integration tests modify shared state, so run serially within each file.
  // Files themselves run in parallel across workers (fullyParallel: false
  // means tests within a single file run serially).
  fullyParallel: false,
  workers: 2,

  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,

  reporter: [
    ["list"],
    ["html", { outputFolder: "playwright-integration-report" }],
  ],

  globalSetup: "./e2e/integration/global-setup.ts",
  globalTeardown: "./e2e/integration/global-teardown.ts",

  // Visual snapshots for integration specs (definition / execution pages).
  // Dynamic names and IDs are masked in helpers.expectMainContentScreenshot.
  // Baselines are platform-agnostic — always capture via Docker Chromium.
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.06,
    },
  },
  snapshotPathTemplate: "{testDir}/__snapshots__/{testFileName}/{arg}{ext}",

  use: {
    baseURL: BASE_URL,
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    // Integration tests can be slower due to real API calls.
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    // Fixed viewport so screenshot baselines are comparable across runs.
    viewport: { width: 1280, height: 800 },
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  // Host headed/ui path: build + preview. Docker path sets SKIP_WEBSERVER —
  // compose `app` service already serves dist/ on :1234.
  //
  // In CI, build separately first and set SKIP_WEBSERVER_BUILD=true when using
  // the host webServer path. Docker integration uses SKIP_WEBSERVER instead.
  ...(SKIP_WEBSERVER
    ? {}
    : {
        webServer: {
          command:
            process.env.SKIP_WEBSERVER_BUILD === "true"
              ? "pnpm preview"
              : "pnpm build && pnpm preview",
          url: BASE_URL,
          reuseExistingServer: !process.env.CI,
          timeout: 300_000,
          env: {
            VITE_WF_SERVER: CONDUCTOR_SERVER_URL,
            NODE_OPTIONS:
              process.env.NODE_OPTIONS ?? "--max-old-space-size=8192",
          },
        },
      }),
});
