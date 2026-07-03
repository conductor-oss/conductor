/**
 * Workflow execution search — visual snapshot tests.
 *
 * Covers the filter form on /executions at four standard viewports and the
 * SQL toggle mode. All /api/* calls are mocked so no live backend is needed.
 *
 * Run in Docker for pixel-consistent baselines:
 *   pnpm test:e2e:snapshots
 *
 * Regenerate baselines after intentional UI changes:
 *   pnpm test:e2e:snapshots:update
 */

import { expect, Page, test } from "@playwright/test";
import type { PageAssertionsToHaveScreenshotOptions } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

const SCREENSHOT_CONFIG = {
  maxDiffPixelRatio: 0.03,
  maxDiffPixels: 1500,
};

const VIEWPORTS = [
  { width: 1920, height: 1080, label: "desktop" },
  { width: 1280, height: 800, label: "laptop" },
  { width: 768, height: 1024, label: "tablet" },
  { width: 390, height: 844, label: "mobile" },
];

const gotoExecutions = async (page: Page) => {
  await mockCommonApis(page);
  await page.addInitScript(() => {
    localStorage.setItem(
      "tooltipFlags",
      JSON.stringify({ executionSearch: true }),
    );
  });
  await page.goto("/executions");
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector("#workflow-search-name-dropdown");
  await page.waitForSelector("#search-workflow-btn");
};

const getMaskElements = (p: Page) => [
  p.locator("[data-testid='user-avatar']"),
  p.locator("#linear-indeterminate-progress"),
];

const screenshotAtAllViewports = async (
  page: Page,
  filename: string,
  options: PageAssertionsToHaveScreenshotOptions,
) => {
  for (const { width, height, label } of VIEWPORTS) {
    await page.setViewportSize({ width, height });
    await expect(page).toHaveScreenshot(
      filename.replace(".png", `-${label}.png`),
      options,
    );
  }
  await page.setViewportSize({ width: 1920, height: 1080 });
};

// ─── Filter form ───────────────────────────────────────────────────────────

test.describe("Workflow execution search - filters visual snapshot", () => {
  test("Should match default empty search form state", async ({ page }) => {
    await gotoExecutions(page);

    await screenshotAtAllViewports(page, "execution-search-default-state.png", {
      mask: getMaskElements(page),
      ...SCREENSHOT_CONFIG,
    });
  });

  test("Should match search form with workflow ID filter filled", async ({
    page,
  }) => {
    await gotoExecutions(page);

    await page.locator("#workflow-search-id").fill("test-workflow-id-12345");

    await screenshotAtAllViewports(
      page,
      "execution-search-with-workflow-id.png",
      {
        mask: getMaskElements(page),
        ...SCREENSHOT_CONFIG,
      },
    );
  });

  test("Should match search form with status filter applied", async ({
    page,
  }) => {
    await gotoExecutions(page);

    await page.locator("#workflow-search-status").click();
    await page.getByRole("option", { name: "COMPLETED" }).click();
    await page.keyboard.press("Escape");

    await screenshotAtAllViewports(
      page,
      "execution-search-with-status-filter.png",
      {
        mask: getMaskElements(page),
        ...SCREENSHOT_CONFIG,
      },
    );
  });

  test("Should match search form with multiple filters applied", async ({
    page,
  }) => {
    await gotoExecutions(page);

    await page.locator("#workflow-search-status").click();
    await page.getByRole("option", { name: "COMPLETED" }).click();
    await page.locator("#workflow-search-status").click();
    await page.getByRole("option", { name: "FAILED" }).click();
    await page.keyboard.press("Escape");

    await page
      .locator("#workflow-search-correlation-id")
      .fill("my-correlation-id");
    await page.keyboard.press("Enter");

    await screenshotAtAllViewports(
      page,
      "execution-search-with-multiple-filters.png",
      {
        mask: getMaskElements(page),
        ...SCREENSHOT_CONFIG,
      },
    );
  });

  test("Should match search form after reset", async ({ page }) => {
    await gotoExecutions(page);

    await page.locator("#workflow-search-status").click();
    await page.getByRole("option", { name: "COMPLETED" }).click();
    await page.keyboard.press("Escape");
    await page.locator("#workflow-search-id").fill("some-id");

    await page.locator("#reset-workflow-btn").click();
    await page.waitForTimeout(500);

    await screenshotAtAllViewports(page, "execution-search-after-reset.png", {
      mask: getMaskElements(page),
      ...SCREENSHOT_CONFIG,
    });
  });

  test("Should match search results after clicking search", async ({
    page,
  }) => {
    await gotoExecutions(page);

    await page.locator("#workflow-search-status").click();
    await page.getByRole("option", { name: "COMPLETED" }).click();
    await page.keyboard.press("Escape");

    await page.locator("#search-workflow-btn").click();
    await page.waitForTimeout(1000);

    await screenshotAtAllViewports(
      page,
      "execution-search-results-completed.png",
      {
        mask: [
          page.locator("[data-testid='user-avatar']"),
          page.locator("tbody"),
        ],
        ...SCREENSHOT_CONFIG,
      },
    );
  });
});

// ─── SQL toggle mode ───────────────────────────────────────────────────────

test.describe("Workflow execution search - SQL toggle mode visual snapshot", () => {
  test("Should match SQL mode after toggling on", async ({ page }) => {
    await gotoExecutions(page);

    await page.getByLabel("SQL format").click();
    await page.waitForTimeout(300);

    await screenshotAtAllViewports(page, "execution-search-sql-mode.png", {
      mask: getMaskElements(page),
      ...SCREENSHOT_CONFIG,
    });
  });

  test("Should match SQL mode with query entered", async ({ page }) => {
    await gotoExecutions(page);

    await page.getByLabel("SQL format").click();
    await page.waitForTimeout(300);

    await page.locator(".monaco-editor").first().click();
    await page.keyboard.press("Control+A");
    await page.keyboard.type("SELECT * FROM workflow WHERE status='COMPLETED'");

    await screenshotAtAllViewports(
      page,
      "execution-search-sql-mode-with-query.png",
      {
        mask: getMaskElements(page),
        ...SCREENSHOT_CONFIG,
      },
    );
  });

  test("Should match basic mode after toggling SQL off", async ({ page }) => {
    await gotoExecutions(page);

    await page.getByLabel("SQL format").click();
    await page.waitForTimeout(300);
    await page.getByLabel("SQL format").click();
    await page.waitForTimeout(300);

    await screenshotAtAllViewports(
      page,
      "execution-search-sql-mode-toggled-off.png",
      {
        mask: getMaskElements(page),
        ...SCREENSHOT_CONFIG,
      },
    );
  });
});
