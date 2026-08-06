/**
 * Integration tests — Task Definition create & delete
 *
 * Creates a task via the new-task form and deletes it from the list page
 * using the typed confirmation dialog.
 */

import { expect, test } from "@playwright/test";
import { deleteTaskDef } from "./api-client";
import { confirmDeleteByTyping, searchDefinitionsList } from "./helpers";

const RUN_ID = Date.now();
const TASK_NAME = `e2e_task_crud_${RUN_ID}`;
const TASK_DESCRIPTION = "Created by Playwright E2E test — safe to delete";

test.describe.configure({ mode: "serial" });

test.afterAll(async () => {
  await deleteTaskDef(TASK_NAME).catch(() => {});
});

test("creates a task definition via the UI", async ({ page }) => {
  await page.goto("/newTaskDef");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/newTaskDef/);
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#task-form-container")).toBeVisible();

  await page.locator("#task-name-field").fill(TASK_NAME);
  await page.locator("#task-description-field").fill(TASK_DESCRIPTION);

  // New-task Save opens an inline confirm step.
  await page.locator("#task-save-btn").click();
  await expect(page.locator("#task-confirm-save-btn")).toBeVisible();
  await page.locator("#task-confirm-save-btn").click();

  await expect(
    page.getByText("Task definition saved successfully."),
  ).toBeVisible({ timeout: 15_000 });

  // Successful create redirects to the edit URL for the new task.
  await expect(page).toHaveURL(new RegExp(`/taskDef/${TASK_NAME}`), {
    timeout: 15_000,
  });

  await searchDefinitionsList(
    page,
    "/taskDef",
    TASK_NAME,
    "Search task definitions",
  );
  await expect(
    page.locator("#main-content").getByText(TASK_NAME),
  ).toBeVisible();
});

test("deletes a task definition via the UI", async ({ page }) => {
  await searchDefinitionsList(
    page,
    "/taskDef",
    TASK_NAME,
    "Search task definitions",
  );
  await expect(
    page.locator("#main-content").getByText(TASK_NAME),
  ).toBeVisible();

  await page.locator(`#delete-${TASK_NAME}-btn`).click();
  await confirmDeleteByTyping(page, TASK_NAME);

  await expect(page.locator("#main-content").getByText(TASK_NAME)).toHaveCount(
    0,
    { timeout: 15_000 },
  );
});
