/**
 * Shared Playwright helpers for UI integration tests.
 */

import { expect, type Locator, type Page } from "@playwright/test";

/** Confirms a ConfirmChoiceDialog that requires typing the resource name. */
export async function confirmDeleteByTyping(
  page: Page,
  name: string,
): Promise<void> {
  await expect(page.locator("#confirm-choice-dialog")).toBeVisible();
  await page.locator("#choice-dialog-confirmation-field").fill(name);
  await expect(page.locator("#choice-dialog-confirm-btn")).toBeEnabled();
  await page.locator("#choice-dialog-confirm-btn").click();
  await expect(page.locator("#confirm-choice-dialog")).toBeHidden();
}

/** Opens a definition list page and filters via the DataTable quick search. */
export async function searchDefinitionsList(
  page: Page,
  path: string,
  searchTerm: string,
  placeholder: string,
): Promise<void> {
  await page.goto(path);
  await page.waitForLoadState("networkidle");
  await page.getByPlaceholder(placeholder).fill(searchTerm);
}

/**
 * Locators covering run-specific / volatile UI so integration visual snapshots
 * stay stable across RUN_IDs, timestamps, and execution UUIDs.
 */
export function dynamicContentMasks(
  page: Page,
  ...extra: Array<string | Locator>
): Locator[] {
  const masks: Locator[] = [
    page.locator("#linear-indeterminate-progress"),
    // Per-run resource names: e2e_<slug>_<epoch>
    page.getByText(/e2e_[a-z0-9_]+_\d{10,}/),
    // Workflow / scheduler execution UUIDs
    page.getByText(
      /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i,
    ),
    // Absolute timestamps / next-run times commonly shown in tables
    page.getByText(
      /\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+\d{1,2},?\s+\d{4}\b/,
    ),
    page.getByText(/\d{1,2}:\d{2}(?::\d{2})?\s*(?:AM|PM)?/i),
  ];

  for (const item of extra) {
    masks.push(typeof item === "string" ? page.getByText(item) : item);
  }
  return masks;
}

/**
 * Execution diagrams can look washed out if we screenshot too early:
 * 1. XState `diagramRenderer.inconsistent` sets `#viewport-container` to opacity 0.5
 * 2. Task refs appear in the DOM before reaflow finishes laying out cards, and
 *    before execution status replaces PENDING (grayscale) styling
 *
 * Wait for full viewport opacity and a fully painted operator task card
 * (SET_VARIABLE etc. use theme.taskCard.operators.background `#205668`).
 */
export async function waitForExecutionDiagramReady(page: Page): Promise<void> {
  const viewport = page.locator("#viewport-container");
  await expect(viewport).toBeVisible({ timeout: 15_000 });
  await expect
    .poll(async () => viewport.evaluate((el) => getComputedStyle(el).opacity), {
      timeout: 15_000,
    })
    .toBe("1");

  await expect
    .poll(
      async () =>
        viewport.evaluate((el) => {
          // Operator cards (SET_VARIABLE, JOIN, …) use #205668. Require a
          // real laid-out size — refs can exist while nodes are still ghosts.
          return [...el.querySelectorAll("div")].some((node) => {
            if (getComputedStyle(node).backgroundColor !== "rgb(32, 86, 104)") {
              return false;
            }
            const { width, height } = node.getBoundingClientRect();
            return width > 100 && height > 40;
          });
        }),
      { timeout: 15_000 },
    )
    .toBe(true);
}

/**
 * Larger viewport for tall workflow diagrams so more of the graph is in frame.
 * Integration default is 1280×800; multi-task topologies need more height.
 */
export async function setDiagramSnapshotViewport(page: Page): Promise<void> {
  await page.setViewportSize({ width: 1440, height: 1200 });
}

/** Zoom the diagram so the full graph fits the current viewport. */
export async function fitDiagramToScreen(page: Page): Promise<void> {
  const fit = page.locator("#fit-screen-button");
  await expect(fit).toBeVisible({ timeout: 15_000 });
  await expect(fit).toBeEnabled();
  await fit.click();
  // Dismiss the "Fit to screen" tooltip so it does not appear in snapshots.
  await page.mouse.move(0, 0);
}

/**
 * Screenshot `#main-content` with dynamic fields masked. Integration runs use
 * unique names/IDs every time, so baselines compare layout/structure rather
 * than exact text.
 */
export async function expectMainContentScreenshot(
  page: Page,
  snapshotName: string,
  {
    mask = [],
    maxDiffPixelRatio = 0.06,
    // "disabled" freezes reaflow/SVG task cards mid-fade (near-invisible).
    animations = "allow",
  }: {
    mask?: Locator[];
    maxDiffPixelRatio?: number;
    animations?: "disabled" | "allow";
  } = {},
): Promise<void> {
  const main = page.locator("#main-content");
  await expect(main).toBeVisible();
  await expect(main).toHaveScreenshot(snapshotName, {
    animations,
    caret: "hide",
    maxDiffPixelRatio,
    mask: [...dynamicContentMasks(page), ...mask],
  });
}
