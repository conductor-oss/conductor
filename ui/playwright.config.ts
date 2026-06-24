import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright E2E test configuration.
 *
 * Tests live in e2e/ and run against the production bundle served on port 5000.
 * The server proxies /api to a Conductor backend (default: localhost:7001);
 * individual test files use page.route() to intercept and mock those calls so
 * the suite can run without a live backend.
 *
 * See https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: "./e2e",

  fullyParallel: true,

  // Fail the build on CI if test.only is accidentally committed.
  forbidOnly: !!process.env.CI,

  retries: process.env.CI ? 2 : 0,

  // Limit concurrency on CI to avoid resource contention.
  workers: process.env.CI ? 1 : undefined,

  reporter: [["html", { outputFolder: "playwright-report" }]],

  use: {
    baseURL: "http://localhost:5000",

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

  // Serve the production bundle before the test run.
  // In CI the bundle is already built by a prior step, so just serve it.
  // Locally, build first then serve; an existing server on port 5000 is reused.
  webServer: {
    command: process.env.CI ? "yarn preview" : "yarn build && yarn preview",
    url: "http://localhost:5000",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
