/**
 * Playwright test fixture that collects V8 JS coverage during each test.
 *
 * Usage: import `{ test, expect }` from this module instead of
 * `@playwright/test` in any spec file that should contribute to coverage.
 *
 * Coverage collection is gated behind the `E2E_COVERAGE` environment variable.
 * When `E2E_COVERAGE=true`, each test writes a raw V8 coverage JSON file to
 * `.playwright-coverage/`.  A post-test script (`scripts/playwright-coverage-report.mjs`)
 * then merges and converts these into an Istanbul report with threshold enforcement.
 *
 * When `E2E_COVERAGE` is unset or false, the fixture is a transparent no-op.
 */

import { test as base, expect } from "@playwright/test";
import { writeFileSync, mkdirSync } from "fs";
import { resolve, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const COVERAGE_DIR = resolve(__dirname, "../.playwright-coverage");

const COLLECT = process.env.E2E_COVERAGE === "true";

if (COLLECT) {
  mkdirSync(COVERAGE_DIR, { recursive: true });
}

let fileIndex = 0;

export const test = base.extend({
  page: async ({ page }, use) => {
    if (COLLECT) {
      await page.coverage.startJSCoverage({ resetOnNavigation: false });
    }

    await use(page);

    if (COLLECT) {
      const entries = await page.coverage.stopJSCoverage();
      const fileName = `coverage-${process.pid}-${fileIndex++}.json`;
      writeFileSync(resolve(COVERAGE_DIR, fileName), JSON.stringify(entries));
    }
  },
});

export { expect };
