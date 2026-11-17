import { expect, test } from "@playwright/test";
import { mockCommonApis, FIVE_CASE_SWITCH_WORKFLOW } from "./helpers/mockApi";

test.describe("Workflow version dropdown (#1590)", () => {
  test.beforeEach(async ({ page }) => {
    // 1. Generic catch-all routes
    await mockCommonApis(page);

    // 2. Mock cluster-wide /api/metadata/workflow to simulate OSS behavior:
    // GET /api/metadata/workflow returns every definition in the cluster (including
    // unrelated workflows with versions 3 and 99) alongside five_case_switch.
    // The client-side filter must exclude the unrelated definitions (#1590).
    await page.route("**/api/metadata/workflow*", (route) => {
      const url = route.request().url();
      if (url.includes("/metadata/workflow/five_case_switch")) {
        return route.fulfill({ json: FIVE_CASE_SWITCH_WORKFLOW });
      }
      return route.fulfill({
        json: [
          { name: "other_workflow_a", version: 3 },
          { name: "other_workflow_b", version: 99 },
          { name: "five_case_switch", version: 1 },
          { name: "five_case_switch", version: 2 },
        ],
      });
    });
  });

  test("only renders versions that exist for the workflow and excludes non-existent versions", async ({
    page,
  }) => {
    await page.goto("/workflowDef/five_case_switch/1", {
      waitUntil: "domcontentloaded",
    });

    // Wait for the workflow header to be ready
    await expect(page.locator("#workflow-name-display")).toBeVisible({
      timeout: 15_000,
    });

    // Locate the version selector button in the metadata bar
    const metaBar = page.locator("#workflow-meta-bar");
    const versionSelectButton = metaBar.getByRole("button", {
      name: /Version 1/i,
    });
    await expect(versionSelectButton).toBeVisible();

    // Open the version dropdown menu
    await versionSelectButton.click();

    const menu = page.locator("#head-bar-menu");
    await expect(menu).toBeVisible();

    const menuItems = menu.getByRole("menuitem");
    // Should list Version 1, Version 2, and "Latest version"
    await expect(menuItems).toHaveCount(3);
    await expect(menuItems).toHaveText([
      "Version 1",
      "Version 2",
      "Latest version",
    ]);

    // Explicitly verify non-existent versions (e.g. Version 3, Version 99) are strictly NOT present
    await expect(menu.getByRole("menuitem", { name: "Version 3" })).toHaveCount(
      0,
    );
    await expect(
      menu.getByRole("menuitem", { name: "Version 99" }),
    ).toHaveCount(0);
  });
});
