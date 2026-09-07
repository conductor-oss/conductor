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

import { expect, test, type Locator } from "@playwright/test";
import { readFile } from "fs/promises";
import {
  mockCommonApis,
  mockMissingSchemaRegistry,
  mockSchemaReferencingTaskDef,
  mockSchemaRegistry,
  mockSwitchWorkflowDef,
} from "./helpers/mockApi";

/**
 * Wait until a locator stops changing on screen.
 *
 * The diagram is rendered with reaflow's entry animation (`animated={true}`)
 * and the viewport auto-fits afterwards, so the graph keeps moving for a while
 * after its nodes are visible. `toHaveScreenshot` copes with that on its own by
 * retrying until two consecutive screenshots match, but an assertion on a
 * downloaded file has no such retry: exporting mid-animation would bake a
 * half-faded diagram into the PNG.
 */
async function waitForStableRender(locator: Locator): Promise<void> {
  let previous: Buffer | undefined;

  await expect(async () => {
    const current = await locator.screenshot();
    const unchanged = previous?.equals(current) ?? false;
    previous = current;
    expect(unchanged, "view is still animating").toBe(true);
  }).toPass({ intervals: [250], timeout: 20_000 });
}

// ---------------------------------------------------------------------------
// App shell (layout) + main list pages
// ---------------------------------------------------------------------------

test.describe("app shell and main pages", () => {
  test.beforeEach(async ({ page }) => {
    await mockCommonApis(page);
  });

  test("app shell layout", async ({ page }) => {
    await page.goto("/");
    // Wait for the sidebar and main content to both be present before snapping
    // the full viewport so we catch structural/navigation regressions.
    await expect(page.locator("#app-sidebar")).toBeVisible();
    await expect(page.locator("#main-content")).toBeVisible();
    await expect(page).toHaveScreenshot("app-shell.png");
  });

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
    await expect(page.locator("#main-content")).toHaveScreenshot(
      "task-defs.png",
    );
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
});

// ---------------------------------------------------------------------------
// Schema management screen
// ---------------------------------------------------------------------------

test.describe("schema management screen", () => {
  test.beforeEach(async ({ page }) => {
    await mockCommonApis(page);
    // Specific registry routes registered last so they win over the catch-all.
    await mockSchemaRegistry(page);
  });

  test("schemas page", async ({ page }) => {
    await page.goto("/schemas");
    await expect(page.locator("#main-content")).toBeVisible();
    // One row per schema, carrying its latest version — so both fixture
    // schemas must be on screen before the page is snapped.
    await expect(page.getByRole("link", { name: "order_input" })).toBeVisible();
    await expect(
      page.getByRole("link", { name: "shipment_event" }),
    ).toBeVisible();
    await expect(page.locator("#main-content")).toHaveScreenshot("schemas.png");
  });

  // A schema of a type this server stores but cannot validate. The editor is
  // read-only and says so, which is the state a screenshot is worth having:
  // nothing about a read-only Monaco looks different from an editable one.
  test("schema editor on a schema this server cannot validate", async ({
    page,
  }) => {
    await page.goto("/schemas/shipment_event/1");
    await expect(page.locator("#main-content")).toBeVisible();
    await expect(page.locator("#schema-read-only-notice")).toBeVisible();
    await expect(page.getByRole("button", { name: "Save" })).toBeDisabled();
    // Monaco is loaded from its own chunk and shows "Loading..." until it
    // arrives. Wait for the schema itself to be on screen, or the baseline
    // records the placeholder.
    await expect(page.locator(".monaco-editor .view-lines")).toContainText(
      "record",
    );
    await expect(page.locator("#main-content")).toHaveScreenshot(
      "schema-editor-read-only.png",
    );
  });
});

// ---------------------------------------------------------------------------
// Schema pickers on a task definition
//
// The pair this unit of work exists for: the same form against a server with no
// schema registry, and against one that serves it. The suite mocks the API, so
// both states are reachable from one build.
// ---------------------------------------------------------------------------

test.describe("schema pickers", () => {
  test.beforeEach(async ({ page }) => {
    await mockCommonApis(page);
    await mockSchemaReferencingTaskDef(page);
  });

  test("pickers with no schema registry on the server", async ({ page }) => {
    await mockMissingSchemaRegistry(page);

    await page.goto("/taskDef/process_order");
    const section = page.locator("#task-schema-section");
    await expect(section).toBeVisible();

    // The defect: the picker has nothing to offer and says nothing about why.
    // The name it was given still shows, and its version cannot be resolved.
    await section.getByLabel("Input Schema").click();
    await expect(page.getByRole("option")).toHaveCount(0);
    await page.keyboard.press("Escape");

    await expect(section).toHaveScreenshot(
      "task-def-schema-pickers-no-registry.png",
    );
  });

  test("pickers against a served schema registry", async ({ page }) => {
    await mockSchemaRegistry(page);

    await page.goto("/taskDef/process_order");
    const section = page.locator("#task-schema-section");
    await expect(section).toBeVisible();

    // One option per registered name, and the referenced version resolves.
    await section.getByLabel("Input Schema").click();
    await expect(page.getByRole("option")).toHaveText([
      "order_input",
      "shipment_event",
    ]);
    await page.keyboard.press("Escape");

    await expect(section).toHaveScreenshot("task-def-schema-pickers.png");
  });
});

// ---------------------------------------------------------------------------
// Workflow definition detail - reaflow diagram view
//
// These tests snapshot the graph panel rendered by reaflow for a specific
// workflow definition. Playwright uses last-registered-wins semantics (new
// handlers are unshifted to the front of the internal match list), so
// mockCommonApis must be registered FIRST and the per-workflow override must
// be registered LAST so it takes precedence over the broad catch-all.
// ---------------------------------------------------------------------------

test.describe("workflow definition detail", () => {
  test.beforeEach(async ({ page }) => {
    // Generic catch-all routes registered first (lower priority).
    await mockCommonApis(page);
    // Specific workflow fixture registered last (higher priority, wins).
    await mockSwitchWorkflowDef(page);
  });

  test("five-case switch workflow diagram", async ({ page }) => {
    await page.goto("/workflowDef/five_case_switch/1");

    // networkidle ensures the workflow fetch has resolved and the XState
    // machine has transitioned to "ready". Only then does the reaflow ELK
    // layout run and populate the graph with nodes.
    await page.waitForLoadState("networkidle");

    // WorkflowMetaBar (which contains #workflow-name-display) only renders
    // when isReady is true, so this confirms the machine finished loading.
    await expect(page.locator("#workflow-name-display")).toBeVisible();

    // Wait for every SWITCH branch node to be present before snapping.
    // Use .first() on the SWITCH node itself because reaflow renders its ref
    // name twice: once in the task node and once in the SWITCH_JOIN pseudo-node.
    await expect(page.getByText("route_by_priority_ref").first()).toBeVisible();
    await expect(page.getByText("notify_urgent_ref")).toBeVisible();
    await expect(page.getByText("notify_high_ref")).toBeVisible();
    await expect(page.getByText("notify_medium_ref")).toBeVisible();
    await expect(page.getByText("notify_low_ref")).toBeVisible();
    await expect(page.getByText("notify_bulk_ref")).toBeVisible();
    await expect(page.getByText("notify_fallback_ref")).toBeVisible();

    await expect(page.locator("#main-content")).toHaveScreenshot(
      "switch-workflow-diagram.png",
    );
  });

  // "Export to image" re-renders the diagram through dom-to-image rather than
  // reusing what is on screen, so it regresses independently of the screenshot
  // above: the SWITCH diamond once exported solid black because dom-to-image
  // inlines computed styles onto its clone, and a malformed data: URI once made
  // the export fail outright. Assert on the downloaded file to cover both.
  test("five-case switch workflow exports to an image", async ({ page }) => {
    await page.goto("/workflowDef/five_case_switch/1");
    await page.waitForLoadState("networkidle");
    await expect(page.locator("#workflow-name-display")).toBeVisible();
    await expect(page.getByText("route_by_priority_ref").first()).toBeVisible();
    await expect(page.getByText("notify_fallback_ref")).toBeVisible();

    await waitForStableRender(page.locator("#main-content"));

    const downloaded = page.waitForEvent("download");
    await page.locator("#print-screen-button").click();
    const download = await downloaded;

    // A rejected export downloads nothing, so arriving here already proves the
    // export succeeded. The name confirms it is the workflow's own diagram.
    expect(download.suggestedFilename()).toBe("five_case_switch.png");

    // Compared far more strictly than the screenshots above, which allow 2% of
    // pixels to differ. That budget is meaningless here: the exported diagram is
    // 2400x1161, so a SWITCH diamond filled solid black is only ~1.4% of the
    // image and would pass. The export runs in Docker precisely so rendering is
    // reproducible, so it can afford to be near-exact.
    expect(await readFile(await download.path())).toMatchSnapshot(
      "switch-workflow-export.png",
      { maxDiffPixels: 200 },
    );
  });
});
