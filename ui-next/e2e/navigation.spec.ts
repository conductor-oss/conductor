/**
 * Navigation tests — verify that client-side navigation between the main
 * pages works and that each page renders its primary content region.
 *
 * All /api/* calls are mocked so no live backend is required.
 */

import { expect, test } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

test.beforeEach(async ({ page }) => {
  await mockCommonApis(page);
  await page.goto("/");
  // Wait for the app shell to be present before interacting
  await expect(page.locator("#main-content")).toBeVisible();
});

test("default route renders the workflow executions search page", async ({
  page,
}) => {
  // The index route maps to WorkflowSearch (/executions)
  await expect(page).toHaveURL(/\/(executions)?$/);
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigates to /executions", async ({ page }) => {
  await page.goto("/executions");
  await expect(page).toHaveURL(/\/executions/);
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigates to workflow definitions /workflowDef", async ({ page }) => {
  await page.goto("/workflowDef");
  await expect(page).toHaveURL(/\/workflowDef/);
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigates to task definitions /taskDef", async ({ page }) => {
  await page.goto("/taskDef");
  await expect(page).toHaveURL(/\/taskDef/);
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigates to scheduler definitions /scheduleDef", async ({ page }) => {
  await page.goto("/scheduleDef");
  await expect(page).toHaveURL(/\/scheduleDef/);
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigates to event handler definitions /eventHandlerDef", async ({
  page,
}) => {
  await page.goto("/eventHandlerDef");
  await expect(page).toHaveURL(/\/eventHandlerDef/);
  await expect(page.locator("#main-content")).toBeVisible();
});

test("unknown route renders the error page", async ({ page }) => {
  await page.goto("/this-route-does-not-exist");
  // React Router renders ErrorPage for the wildcard "*" route.
  // ErrorPage uses MuiTypography (renders as <p>, not a heading element)
  // so we check for the error-page container and visible text instead.
  await expect(page.locator("#error-page")).toBeVisible();
  await expect(page.locator("#error-page")).toContainText(/error/i);
});
