/**
 * Playwright configuration for snapshot tests.
 *
 * Intended to be run inside Docker via docker-compose.snapshots.yml so that
 * Chromium rendering is identical across developer machines and CI. The app
 * service in the compose file manages the Vite dev server; this config has no
 * webServer block and reads the URL from the BASE_URL environment variable
 * (set by Docker Compose to http://app:1234).
 *
 * To run locally:
 *   pnpm test:e2e:snapshots
 *
 * To regenerate baselines:
 *   pnpm test:e2e:snapshots:update
 */

import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: ["**/*.spec.ts"],
  testIgnore: ["**/integration/**"],

  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,

  reporter: [["html", { outputFolder: "playwright-snapshots-report" }]],

  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.02,
    },
  },

  snapshotPathTemplate: "{testDir}/__snapshots__/{testFilePath}/{arg}{ext}",

  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:1234",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
