/**
 * Integration tests — Workflow Executions
 *
 * Starts real workflow executions via the API and verifies the search UI
 * can find and display them.  Uses a SET_VARIABLE workflow so executions
 * reach COMPLETED state immediately without needing a worker process.
 */

import { expect, test } from "@playwright/test";
import {
  createWorkflowDef,
  deleteWorkflowDef,
  startWorkflow,
  terminateWorkflow,
  type WorkflowDef,
} from "./api-client";

const RUN_ID = Date.now();
const WF_NAME = `e2e_exec_${RUN_ID}`;

const WORKFLOW_DEF: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description: "Created by Playwright E2E test — safe to delete",
  tasks: [
    {
      name: "set_var",
      taskReferenceName: "set_var_ref",
      type: "SET_VARIABLE",
      inputParameters: { result: "e2e-test-value" },
    },
  ],
};

// IDs of executions we start — cleaned up in afterAll.
const startedWorkflowIds: string[] = [];

test.beforeAll(async () => {
  await createWorkflowDef(WORKFLOW_DEF);
});

test.afterAll(async () => {
  // Terminate any running executions before deleting the definition.
  await Promise.allSettled(
    startedWorkflowIds.map((id) => terminateWorkflow(id)),
  );
  await deleteWorkflowDef(WF_NAME).catch(() => {});
});

// ── Helpers ────────────────────────────────────────────────────────────────────

/** Navigates to /executions with the workflow type pre-filled and waits for
 *  the results table to finish loading. */
async function openExecutionsSearch(
  page: import("@playwright/test").Page,
  workflowType: string,
) {
  // The WorkflowSearch page reads its initial state from URL query params.
  // workflowType is the most reliable filter to narrow results to our test data.
  await page.goto(
    `/executions?workflowType=${encodeURIComponent(workflowType)}`,
  );
  await page.waitForLoadState("networkidle");
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test("started workflow execution appears in the executions search", async ({
  page,
}) => {
  const workflowId = await startWorkflow(WF_NAME, { value: "test" });
  startedWorkflowIds.push(workflowId);

  await openExecutionsSearch(page, WF_NAME);

  // The workflow type column should show our workflow name.
  await expect(page.getByText(WF_NAME).first()).toBeVisible();
});

test("execution row shows the workflow ID", async ({ page }) => {
  const workflowId = await startWorkflow(WF_NAME, { value: "test" });
  startedWorkflowIds.push(workflowId);

  await openExecutionsSearch(page, WF_NAME);

  // The full ID or a truncated prefix should appear somewhere in the table.
  const idPrefix = workflowId.substring(0, 8);
  await expect(page.getByText(new RegExp(idPrefix))).toBeVisible();
});

test("clicking an execution row opens the execution detail page", async ({
  page,
}) => {
  const workflowId = await startWorkflow(WF_NAME, { value: "test" });
  startedWorkflowIds.push(workflowId);

  await openExecutionsSearch(page, WF_NAME);

  // The workflow ID is unique to the results table — click its prefix so we
  // don't accidentally click the workflow type text in the search filter.
  const idPrefix = workflowId.substring(0, 8);
  await page.getByText(new RegExp(idPrefix)).first().click();

  await expect(page).toHaveURL(new RegExp(`/execution/${workflowId}`));
  await page.waitForLoadState("networkidle");

  // Detail page content
  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(WF_NAME)).toBeVisible();
  await expect(page.getByText(/COMPLETED/i)).toBeVisible();

  // The full workflow ID should appear somewhere on the detail page.
  await expect(page.getByText(new RegExp(workflowId))).toBeVisible();

  // The SET_VARIABLE task should be listed in the task list.
  await expect(page.getByText("set_var_ref")).toBeVisible();
});

test("executions page renders the search form", async ({ page }) => {
  await page.goto("/executions");
  await page.waitForLoadState("networkidle");

  // The page always renders a search form regardless of results.
  await expect(page.locator("#main-content")).toBeVisible();

  // There should be at least one text input for filtering.
  await expect(page.locator("#main-content input").first()).toBeVisible();
});
