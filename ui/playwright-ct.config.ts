import { defineConfig, devices } from "@playwright/experimental-ct-react";

export default defineConfig({
  testDir: "src",
  testMatch: "**/*.test.pw.tsx",
  snapshotDir: "./__snapshots__",
  timeout: 30 * 1000,
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "html",
  use: {
    ctPort: 3100,
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
