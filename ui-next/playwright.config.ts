import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright E2E test configuration.
 *
 * Tests live in e2e/ and run against the Vite dev server on port 1234.
 * The dev server proxies /api to a Conductor backend (default: localhost:7001);
 * individual test files use page.route() to intercept and mock those calls so
 * the suite can run without a live backend.
 *
 * See https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: "./e2e",
  testIgnore: "**/integration/**",

  // Run each test file in parallel; keep serial within a file.
  fullyParallel: true,

  // Fail the build on CI if test.only is accidentally committed.
  forbidOnly: !!process.env.CI,

  retries: process.env.CI ? 2 : 0,

  // Limit concurrency on CI to avoid resource contention.
  workers: process.env.CI ? 1 : undefined,

  reporter: [["html", { outputFolder: "playwright-report" }]],

  // Snapshot comparison settings.
  // Allow up to 2% of pixels to differ to tolerate minor sub-pixel and
  // anti-aliasing differences across machines without false positives.
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.02,
    },
  },

  // Store snapshots next to the spec files in an __snapshots__ directory.
  snapshotPathTemplate: "{testDir}/__snapshots__/{testFilePath}/{arg}{ext}",

  use: {
    baseURL: "http://localhost:1234",

    // Collect traces on the first retry of a failed test for debugging.
    trace: "on-first-retry",

    // Capture screenshots only on failure.
    screenshot: "only-on-failure",
  },

  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],

  // Start the Vite dev server before the test run.
  // In CI a fresh server is always spun up; locally an existing one is reused.
  webServer: {
    command: "pnpm dev",
    url: "http://localhost:1234",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
