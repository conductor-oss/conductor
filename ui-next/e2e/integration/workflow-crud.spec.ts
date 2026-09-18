/**
 * Integration tests — Workflow Definition create & delete
 *
 * Drives the UI to create a workflow (properties + diagram task add + Save)
 * and delete it from the definitions list with typed confirmation.
 */

import { expect, test } from "../coverage-fixture";
import { deleteWorkflowDef } from "./api-client";
import { confirmDeleteByTyping, searchDefinitionsList } from "./helpers";

const RUN_ID = Date.now();
const WF_NAME = `e2e_wf_crud_${RUN_ID}`;
const WF_DESCRIPTION = "Created by Playwright E2E test — safe to delete";

test.describe.configure({ mode: "serial" });

test.afterAll(async () => {
  // Safety net if a create succeeded but delete failed mid-suite.
  await deleteWorkflowDef(WF_NAME).catch(() => {});
});

test("creates a workflow definition via the UI", async ({ page }) => {
  await page.goto("/newWorkflowDef");
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#workflow-name-display")).toBeVisible();

  // Workflow properties tab is open by default for new definitions.
  await page.locator("#workflow-name-field").fill(WF_NAME);
  await page.locator("#workflow-description-field").fill(WF_DESCRIPTION);

  // Add a SET_VARIABLE task via the diagram "+" control so Save enables
  // (Save stays disabled while tasks is empty).
  await page.locator('[id^="ADD-"]').first().click();
  await page.getByText("Set Variable", { exact: true }).click();
  await expect(page.getByText(/set_variable/i).first()).toBeVisible({
    timeout: 10_000,
  });

  await expect(page.locator("#head-action-save-btn")).toBeEnabled({
    timeout: 15_000,
  });
  await page.locator("#head-action-save-btn").click();
  await page.locator("#confirm-saving-btn").click();

  await expect(page.getByText("Workflow saved successfully.")).toBeVisible({
    timeout: 15_000,
  });

  await searchDefinitionsList(
    page,
    "/workflowDef",
    WF_NAME,
    "Search workflow definitions",
  );
  await expect(page.locator("#main-content").getByText(WF_NAME)).toBeVisible();
});

test("deletes a workflow definition via the UI", async ({ page }) => {
  await searchDefinitionsList(
    page,
    "/workflowDef",
    WF_NAME,
    "Search workflow definitions",
  );
  await expect(page.locator("#main-content").getByText(WF_NAME)).toBeVisible();

  await page.locator(`#delete-${WF_NAME}-btn`).click();
  await confirmDeleteByTyping(page, WF_NAME);

  // Row should disappear after the list refetches.
  await expect(page.locator("#main-content").getByText(WF_NAME)).toHaveCount(
    0,
    { timeout: 15_000 },
  );
});
