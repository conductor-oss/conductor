/**
 * Integration tests — Queue Monitor (`/taskQueue`)
 *
 * Visits the queue monitor page so queueMonitor state/filter/refresher
 * modules load under E2E coverage. Empty queues are fine — the page still
 * mounts PollDataTable and the refresh controls.
 */

import { expect, test } from "../coverage-fixture";

test("queue monitor page renders search and table chrome", async ({ page }) => {
  await page.goto("/taskQueue");
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText("Queue Monitor").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByPlaceholder("Quick search")).toBeVisible({
    timeout: 15_000,
  });

  // Column headers from PollDataTable (even when there are no rows).
  await expect(page.getByText("Queue Name").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(
    page.getByText(/No polling details found|Worker Count|Queue Size/).first(),
  ).toBeVisible({ timeout: 15_000 });
});

test("queue monitor refresh control is clickable", async ({ page }) => {
  await page.goto("/taskQueue");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText("Queue Monitor").first()).toBeVisible({
    timeout: 15_000,
  });

  // RefreshOptions shows either a countdown or "Refreshing every second".
  const refresh = page.getByRole("button", { name: /Refresh/i }).first();
  await expect(refresh).toBeVisible({ timeout: 15_000 });
  await refresh.click();
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText("Queue Monitor").first()).toBeVisible();
});
