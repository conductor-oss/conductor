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

// The "Execution Start Time" filter defaults to `Date.now() - 72h`, rendered as
// an absolute timestamp. Left to the live clock the chip drifts every run (and a
// load-timing race can show the "Last 72 Hours" label instead), so the snapshots
// are only stable if the value is pinned. Freeze the clock and seed `startFrom`
// with the matching fixed epoch so the chip always renders "May 29, 2026 @
// 12:00:00"; seeding the query param also settles the value on the first render.
const FIXED_NOW = new Date("2026-06-01T12:00:00.000Z");
const FIXED_START_FROM = FIXED_NOW.getTime() - 72 * 60 * 60 * 1000; // 1780056000000

const gotoExecutions = async (page: Page) => {
  await mockCommonApis(page);
  await page.clock.setFixedTime(FIXED_NOW);
  await page.addInitScript(() => {
    localStorage.setItem(
      "tooltipFlags",
      JSON.stringify({ executionSearch: true }),
    );
  });
  await page.goto(`/executions?startFrom=${FIXED_START_FROM}`);
  await page.waitForLoadState("domcontentloaded");
  await page.waitForSelector("#workflow-search-name-dropdown");
  await page.waitForSelector("#search-workflow-btn");
};

const getMaskElements = (p: Page) => [
  p.locator("[data-testid='user-avatar']"),
  p.locator("#linear-indeterminate-progress"),
];

/**
 * MUI commits an autocomplete selection on the next React render. Wait for its selected tag before
 * moving on so a following action cannot race the state update in CI.
 */
const selectWorkflowStatus = async (page: Page, status: string) => {
  const statusInput = page.locator("#workflow-search-status");
  const statusField = statusInput.locator("xpath=..");
  await statusInput.click();
  await page.getByRole("option", { name: status }).click();
  await expect(statusField).toContainText(
    status.charAt(0) + status.slice(1).toLowerCase(),
  );
  await page.keyboard.press("Escape");
};

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

    await selectWorkflowStatus(page, "COMPLETED");

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

    await selectWorkflowStatus(page, "COMPLETED");
    await selectWorkflowStatus(page, "FAILED");

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

    await selectWorkflowStatus(page, "COMPLETED");
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

    await selectWorkflowStatus(page, "COMPLETED");

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

// ─── Execution start time picker ───────────────────────────────────────────

test.describe("Workflow execution search - start time picker visual snapshot", () => {
  const openStartTimePicker = async (page: Page) => {
    await gotoExecutions(page);
    await page.locator("#date-picker-start-time").locator("p").nth(1).click();
    await expect(page.getByRole("tab", { name: "Presets" })).toBeVisible();
  };

  test("Should match the open start time menu", async ({ page }) => {
    await openStartTimePicker(page);

    await expect(page.locator(".MuiTooltip-popper")).toHaveScreenshot(
      "execution-start-time-picker.png",
      SCREENSHOT_CONFIG,
    );
  });

  test("Should match the Absolute tab calendar with a hovered end date", async ({
    page,
  }) => {
    await openStartTimePicker(page);

    await page.locator("#date-picker-absolute-tab").click();
    const calendar = page.locator(".react-datepicker");
    await expect(calendar).toBeVisible();

    // Weekday letters and the date cells share one column grid.
    const columns = await page.evaluate(() => {
      const center = (el: Element) => {
        const box = el.getBoundingClientRect();
        return box.x + box.width / 2;
      };
      const root = document.querySelector(".react-datepicker")!;
      const names = [...root.querySelectorAll(".react-datepicker__day-name")];
      const days = [
        ...root.querySelectorAll(".react-datepicker__week")[1].children,
      ];
      return names.map((name, index) => ({
        name: center(name),
        day: center(days[index]),
      }));
    });
    for (const column of columns) {
      expect(Math.abs(column.name - column.day)).toBeLessThan(1);
    }

    // Pick a start day, then hover a later day so the snapshot shows the
    // in-progress range instead of a single selected day.
    const startDay = page.getByRole("option", { name: /May 10th, 2026/ });
    const endDay = page.getByRole("option", { name: /May 20th, 2026/ });
    await startDay.click();
    await expect(startDay).toHaveAttribute("aria-selected", "true");

    const selected = await startDay.boundingBox();
    expect(selected).not.toBeNull();
    expect(Math.abs(selected!.width - selected!.height)).toBeLessThan(1);

    const before = await endDay.boundingBox();
    await endDay.hover();
    const after = await endDay.boundingBox();
    expect(before).not.toBeNull();
    expect(after).not.toBeNull();
    expect(Math.abs(after!.x - before!.x)).toBeLessThan(1);
    expect(Math.abs(after!.width - before!.width)).toBeLessThan(1);
    expect(Math.abs(after!.height - before!.height)).toBeLessThan(1);

    await expect(startDay).toHaveClass(/react-datepicker__day--selected/);
    await expect(endDay).toHaveClass(
      /react-datepicker__day--selecting-range-end/,
    );
    await expect(
      page.getByRole("option", { name: /May 15th, 2026/ }),
    ).toHaveClass(/react-datepicker__day--in-selecting-range/);
    await expect(
      page.getByRole("option", { name: /May 21st, 2026/ }),
    ).not.toHaveClass(/react-datepicker__day--in-selecting-range/);

    await expect(page.locator(".MuiTooltip-popper")).toHaveScreenshot(
      "execution-start-time-absolute.png",
      SCREENSHOT_CONFIG,
    );
  });

  test("Should match the Absolute tab calendar with a selected date range", async ({
    page,
  }) => {
    await openStartTimePicker(page);

    await page.locator("#date-picker-absolute-tab").click();
    const calendar = page.locator(".react-datepicker");
    await expect(calendar).toBeVisible();

    const startDay = page.getByRole("option", { name: /May 10th, 2026/ });
    const endDay = page.getByRole("option", { name: /May 20th, 2026/ });
    await startDay.click();
    await endDay.click();

    await expect(startDay).toHaveClass(/react-datepicker__day--range-start/);
    await expect(endDay).toHaveClass(/react-datepicker__day--range-end/);
    await expect(
      page.getByRole("option", { name: /May 15th, 2026/ }),
    ).toHaveClass(/react-datepicker__day--in-range/);
    await expect(
      page.getByRole("option", { name: /May 21st, 2026/ }),
    ).not.toHaveClass(/react-datepicker__day--in-range/);

    // Leave the pointer off the calendar so the snapshot shows the committed
    // range rather than a hover preview.
    await calendar.locator(".react-datepicker__current-month").hover();

    await expect(page.locator(".MuiTooltip-popper")).toHaveScreenshot(
      "execution-start-time-absolute-selected-range.png",
      SCREENSHOT_CONFIG,
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
