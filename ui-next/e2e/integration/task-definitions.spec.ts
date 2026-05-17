/**
 * Integration tests — Task Definitions
 *
 * Creates real task definitions via the API and verifies the UI shows them
 * correctly in the list and in the per-task editor.
 */

import { expect, test } from "@playwright/test";
import { createTaskDef, deleteTaskDef, type TaskDef } from "./api-client";

const RUN_ID = Date.now();

function makeTaskDef(suffix: string): TaskDef {
  return {
    name: `e2e_task_${suffix}_${RUN_ID}`,
    description: "Created by Playwright E2E test — safe to delete",
    retryCount: 0,
    inputKeys: ["input_value"],
    outputKeys: ["output_value"],
  };
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const TASK_LIST = makeTaskDef("list");
const TASK_EDITOR = makeTaskDef("editor");

test.beforeAll(async () => {
  await createTaskDef(TASK_LIST);
  await createTaskDef(TASK_EDITOR);
});

test.afterAll(async () => {
  await deleteTaskDef(TASK_LIST.name).catch(() => {});
  await deleteTaskDef(TASK_EDITOR.name).catch(() => {});
});

// ── Tests ─────────────────────────────────────────────────────────────────────

test("task definition appears in the /taskDef list", async ({ page }) => {
  await page.goto("/taskDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(TASK_LIST.name)).toBeVisible();
});

test("task definition description is shown in the list", async ({ page }) => {
  await page.goto("/taskDef");
  await page.waitForLoadState("networkidle");

  await expect(
    page.getByText("Created by Playwright E2E test — safe to delete").first(),
  ).toBeVisible();
});

test("clicking a task definition opens the task editor", async ({ page }) => {
  await page.goto("/taskDef");
  await page.waitForLoadState("networkidle");

  await page
    .locator("#main-content")
    .getByText(TASK_EDITOR.name)
    .first()
    .click();

  await expect(page).toHaveURL(new RegExp(`/taskDef/${TASK_EDITOR.name}`));
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigating directly to a task definition URL opens the editor", async ({
  page,
}) => {
  await page.goto(`/taskDef/${TASK_EDITOR.name}`);
  await page.waitForLoadState("networkidle");

  // The task name should appear in the page heading or editor.
  await expect(page.getByText(TASK_EDITOR.name).first()).toBeVisible();
});

test("navigating to /taskDef/new opens the new task form", async ({ page }) => {
  await page.goto("/taskDef/new");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/taskDef\/new/);
  await expect(page.locator("#main-content")).toBeVisible();
});
