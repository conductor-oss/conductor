/**
 * Regression tests for the workflow definition editor, specifically around
 * creating new workflows.
 *
 * Regression: useWorkflowVersions() was called with a non-null query key even
 * when workflowName was null/undefined, causing it to fire spurious API requests
 * (and return stale data via keepPreviousData) when opening a new workflow.
 * Fix: pass `null` as the key when workflowName is falsy so react-query
 * treats the query as fully disabled.
 *
 * Relevant code: src/data/workflow.js — useWorkflowVersions()
 */
import { expect, test } from "@playwright/test";
import { f, mockCommonApis } from "./helpers/mockApi";

test.describe("Workflow Definition editor", () => {
  test.beforeEach(async ({ page }) => {
    await mockCommonApis(page);
    await page.route("**/api/metadata/workflow", (route) =>
      route.fulfill({ path: f("metadataWorkflow.json") })
    );
  });

  test("New workflow — editor loads without making a versions API call", async ({
    page,
  }) => {
    const versionsRequests: string[] = [];

    // Capture any /versions requests so we can assert none fire for a null name.
    // A request to e.g. /api/metadata/workflow/undefined/versions or
    // /api/metadata/workflow//versions would indicate the regression is present.
    await page.route("**/api/metadata/workflow/*/versions", (route) => {
      versionsRequests.push(route.request().url());
      route.fulfill({ path: f("metadataWorkflowVersions.json") });
    });

    await page.goto("/workflowDef");

    // Toolbar must show "NEW" — confirms workflowName is null and the
    // template was loaded rather than an existing definition
    await expect(page.getByText("NEW")).toBeVisible();

    // Save button must be present
    await expect(page.getByRole("button", { name: "Save" })).toBeVisible();

    // No /versions request should have fired: the query key guard in
    // useWorkflowVersions must be returning null when workflowName is falsy
    const badRequest = versionsRequests.find((url) =>
      /\/metadata\/workflow\/(undefined|null|)\//i.test(url)
    );
    expect(
      badRequest,
      `Spurious versions request fired with invalid workflow name: ${badRequest}`
    ).toBeUndefined();
  });

  test("New workflow — page stays stable and no spurious versions calls fire during page lifecycle", async ({
    page,
  }) => {
    // The Save dialog calls useWorkflowVersions(parsedName) where parsedName
    // is derived from the editor JSON. For a new workflow the template has
    // name: "", so parsedName = "" (falsy). The null-key guard must prevent a
    // fetch throughout the page lifecycle, not just at initial mount.
    const versionsRequests: string[] = [];
    await page.route("**/api/metadata/workflow/*/versions", (route) => {
      versionsRequests.push(route.request().url());
      route.fulfill({ path: f("metadataWorkflowVersions.json") });
    });

    await page.goto("/workflowDef");
    await expect(page.getByText("NEW")).toBeVisible();

    // Save button must be rendered (it will be disabled until the editor is
    // modified, which is expected behaviour for an unmodified template)
    await expect(page.getByRole("button", { name: "Save" })).toBeVisible();

    // Wait a beat to let any deferred/debounced effects settle
    await page.waitForTimeout(1000);

    // No /versions request should have fired with an empty, null, or undefined
    // workflow name at any point during the page lifecycle
    const badRequest = versionsRequests.find((url) =>
      /\/metadata\/workflow\/(undefined|null|)\//i.test(url)
    );
    expect(
      badRequest,
      `Spurious versions request fired with invalid workflow name: ${badRequest}`
    ).toBeUndefined();
  });

  test("Existing workflow — editor loads and versions are fetched", async ({
    page,
  }) => {
    // useWorkflowDef fetches GET /api/metadata/workflow/:name
    // useWorkflowVersions fetches GET /api/metadata/workflow/:name/versions
    await page.route("**/api/metadata/workflow/19test009", (route) =>
      route.fulfill({ path: f("metadataWorkflow.json") })
    );
    await page.route("**/api/metadata/workflow/19test009/versions", (route) =>
      route.fulfill({ path: f("metadataWorkflowVersions.json") })
    );

    await page.goto("/workflowDef/19test009");

    // Toolbar shows the workflow name (not "NEW").
    // Use exact: true so the Monaco editor's JSON span ("19test009" with
    // surrounding quotes) does not create a strict-mode ambiguity.
    await expect(page.getByText("19test009", { exact: true })).toBeVisible();

    // Version selector is rendered and defaults to "Latest Version".
    // MUI v4 Select renders as role="button" (not "combobox"), so target by text.
    await expect(page.getByText("Latest Version")).toBeVisible();
  });
});
