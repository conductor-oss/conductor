/**
 * UI snapshot tests — guard against unintended visual regressions.
 *
 * Each test navigates to a main page, waits for the content to fully settle,
 * then compares `#main-content` against a stored baseline screenshot.
 *
 * Runs inside Docker (via docker-compose.snapshots.yml) so every machine and
 * CI environment produces pixel-identical baselines. All /api/* calls are
 * mocked so the suite is deterministic and requires no live backend.
 *
 * --------------------------------------------------------------------------
 * Updating baselines
 * --------------------------------------------------------------------------
 * When an intentional UI change causes a snapshot diff, regenerate the
 * baselines with:
 *
 *   pnpm test:e2e:snapshots:update
 *
 * Review the updated images in git before committing to confirm the changes
 * are expected.
 * --------------------------------------------------------------------------
 */

import { expect, test } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

test.beforeEach(async ({ page }) => {
  await mockCommonApis(page);
});

// ---------------------------------------------------------------------------
// App shell (layout)
// ---------------------------------------------------------------------------

test("app shell layout", async ({ page }) => {
  await page.goto("/");
  // Wait for the sidebar and main content to both be present before snapping
  // the full viewport so we catch structural/navigation regressions.
  await expect(page.locator("#app-sidebar")).toBeVisible();
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page).toHaveScreenshot("app-shell.png");
});

// ---------------------------------------------------------------------------
// Main pages — snapshot the primary content area for each route
// ---------------------------------------------------------------------------

test("workflow executions page", async ({ page }) => {
  await page.goto("/executions");
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content")).toHaveScreenshot(
    "executions.png",
  );
});

test("workflow definitions page", async ({ page }) => {
  await page.goto("/workflowDef");
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content")).toHaveScreenshot(
    "workflow-defs.png",
  );
});

test("task definitions page", async ({ page }) => {
  await page.goto("/taskDef");
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content")).toHaveScreenshot("task-defs.png");
});

test("scheduler definitions page", async ({ page }) => {
  await page.goto("/scheduleDef");
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content")).toHaveScreenshot(
    "scheduler-defs.png",
  );
});

test("event handler definitions page", async ({ page }) => {
  await page.goto("/eventHandlerDef");
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content")).toHaveScreenshot(
    "event-handler-defs.png",
  );
});
