import { expect, test } from "@playwright/test";
import {
  f,
  mockCommonApis,
  mockTaskSearch,
  mockWorkflowSearch,
} from "./helpers/mockApi";

test.describe("Landing Page", () => {
  test.beforeEach(async ({ page }) => {
    await mockCommonApis(page);
    await mockWorkflowSearch(page);
    await mockTaskSearch(page);
  });

  test("Homepage preloads with default query", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByText("Search Execution")).toBeVisible();
    await expect(page.getByText("Page 1 of 1")).toBeVisible();
    await expect(
      page
        .locator(".rdt_TableCell")
        .filter({ hasText: "feature_value_compute_workflow" })
        .first()
    ).toBeVisible();
  });

  test("Workflow name dropdown", async ({ page }) => {
    await page.goto("/");
    await page.locator(".MuiAutocomplete-inputRoot input").first().click();
    await page
      .locator("li.MuiAutocomplete-option")
      .filter({ hasText: "Do_While_Workflow_Iteration_Fix" })
      .click();
    await expect(
      page
        .locator(".MuiAutocomplete-tag")
        .filter({ hasText: "Do_While_Workflow_Iteration_Fix" })
    ).toBeVisible();
  });

  test("Switch to Task Tab - No results", async ({ page }) => {
    await page.goto("/");
    await page.locator("a.MuiTab-root").filter({ hasText: "Tasks" }).click();
    await expect(page.getByText("Task Name")).toBeVisible();
    await expect(
      page.getByText("There are no records to display")
    ).toBeVisible();
  });

  test("Task Name Dropdown", async ({ page }) => {
    await page.goto("/");
    await page.locator("a.MuiTab-root").filter({ hasText: "Tasks" }).click();
    await page.locator(".MuiAutocomplete-inputRoot input").first().click();
    await page
      .locator("li.MuiAutocomplete-option")
      .filter({ hasText: "example_task_2" })
      .click();
    await expect(
      page.locator(".MuiAutocomplete-tag").filter({ hasText: "example_task_2" })
    ).toBeVisible();
  });

  test("Execute Task Search", async ({ page }) => {
    await page.goto("/");
    await page.locator("a.MuiTab-root").filter({ hasText: "Tasks" }).click();
    // Select a task name first — useTaskSearch short-circuits to empty results when
    // query and freeText are both empty, so a filter is required to hit the API.
    await page.locator(".MuiAutocomplete-inputRoot input").first().click();
    await page
      .locator("li.MuiAutocomplete-option")
      .filter({ hasText: "example_task_2" })
      .click();
    await page.locator("button").filter({ hasText: "Search" }).click();
    await expect(page.getByText("Page 1 of 1")).toBeVisible();
    await expect(
      page
        .locator(".rdt_TableCell")
        .filter({ hasText: "36d24c5c-9c26-46cf-9709-e1bc6963b8a5" })
        .first()
    ).toBeVisible();
  });
});
