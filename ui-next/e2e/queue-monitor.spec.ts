/**
 * Queue Monitor — visual snapshot tests.
 *
 * Covers the compound filters, interval picker, queue table, search, and
 * worker-details panel. All /api/* calls are mocked so no live backend is
 * needed. The countdown on the refresh button is masked because it ticks.
 *
 * Run in Docker for pixel-consistent baselines:
 *   pnpm test:e2e:snapshots
 *
 * Regenerate baselines after intentional UI changes:
 *   pnpm test:e2e:snapshots:update
 */

import { expect, test, type Page } from "@playwright/test";
import { mockCommonApis, mockQueueMonitorApis } from "./helpers/mockApi";

const screenshotMasks = (page: Page) => [
  page.locator("[data-testid='user-avatar']"),
  page.getByRole("button", { name: /Refresh in|Every second/i }),
];

test.describe("queue monitor", () => {
  test.beforeEach(async ({ page }) => {
    await mockCommonApis(page);
    await mockQueueMonitorApis(page);
    await page.addInitScript(() => {
      window.localStorage.setItem("queueMonitorRefreshSeconds", "60");
    });
  });

  test("list, search, and worker details", async ({ page }) => {
    await page.goto("/taskQueue");
    await page.waitForLoadState("networkidle");

    await expect(page.getByText("Queue Monitor").first()).toBeVisible();
    await expect(page.getByPlaceholder("Quick search")).toBeVisible();
    await expect(page.getByText("send_email")).toBeVisible();
    await expect(page.getByText("process_payment")).toBeVisible();
    await expect(page.getByText("idle_queue")).toBeVisible();
    await expect(page.getByText("Queue size", { exact: true })).toBeVisible();
    await expect(
      page.getByRole("button", { name: "1s" }).first(),
    ).toBeVisible();

    await expect(page.locator("#main-content")).toHaveScreenshot(
      "queue-monitor-initial.png",
      { mask: screenshotMasks(page) },
    );

    await page.getByPlaceholder("Quick search").fill("email");
    await expect(page.getByText("send_email")).toBeVisible();
    await expect(page.getByText("process_payment")).not.toBeVisible();
    await expect(page.locator("#main-content")).toHaveScreenshot(
      "queue-monitor-search.png",
      { mask: screenshotMasks(page) },
    );

    await page.getByPlaceholder("Quick search").fill("");
    await expect(page.getByText("idle_queue")).toBeVisible();

    await page.getByRole("radio", { name: "Select queue send_email" }).click();
    await expect(page.getByText("worker-east")).toBeVisible();
    await expect(page.getByText("worker-west")).toBeVisible();
    await expect(page.locator("#main-content")).toHaveScreenshot(
      "queue-monitor-worker-details.png",
      { mask: screenshotMasks(page) },
    );
  });
});
