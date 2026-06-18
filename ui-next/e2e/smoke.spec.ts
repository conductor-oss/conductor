/**
 * Smoke tests — verify the app shell renders correctly without a live backend.
 *
 * These tests mock all /api/* calls so they can run in any environment.
 */

import { expect, test } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

test.beforeEach(async ({ page }) => {
  await mockCommonApis(page);
});

test("page title reflects the current page", async ({ page }) => {
  await page.goto("/");
  // The home route renders WorkflowSearch which sets its own Helmet title.
  await expect(page).toHaveTitle("Workflow Executions");
});

test("app shell renders layout containers", async ({ page }) => {
  await page.goto("/");

  // Top-level layout grid
  await expect(page.locator("#side-and-top-bars-layout")).toBeVisible();

  // Sidebar and main content areas
  await expect(page.locator("#app-sidebar")).toBeVisible();
  await expect(page.locator("#main-content")).toBeVisible();
});

test("sidebar is visible on desktop viewport", async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await page.goto("/");

  // The UISidebar renders inside #app-sidebar
  await expect(page.locator("#app-sidebar")).toBeVisible();

  // The sidebar should not be empty — at least one nav link is present
  const sidebarLinks = page.locator("#app-sidebar a");
  await expect(sidebarLinks.first()).toBeVisible();
});

test("no uncaught JS exceptions on load", async ({ page }) => {
  const errors: string[] = [];
  page.on("pageerror", (err) => errors.push(err.message));

  await page.goto("/");
  // Wait for the app to fully settle
  await page.waitForLoadState("networkidle");

  expect(errors).toHaveLength(0);
});
