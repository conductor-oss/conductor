/**
 * Integration tests — Workflow Executions
 *
 * Starts real workflow executions via the API and verifies the search UI
 * can find and display them.  Uses a SET_VARIABLE workflow so executions
 * reach COMPLETED state immediately without needing a worker process.
 */

import { expect, test } from "../coverage-fixture";
import {
  createWorkflowDef,
  deleteWorkflowDef,
  startWorkflow,
  terminateWorkflow,
  waitForWorkflow,
  type WorkflowDef,
} from "./api-client";

const RUN_ID = Date.now();
const WF_NAME = `e2e_exec_${RUN_ID}`;
const SEARCH_INDEX_TIMEOUT_MS = 45_000;

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

/** Status chips render title case ("Completed"), while task lists may also say COMPLETED. */
async function expectExecutionStatusChip(
  page: import("@playwright/test").Page,
  status: string,
) {
  const label = status.charAt(0) + status.slice(1).toLowerCase();
  await expect(
    page
      .locator(".MuiChip-label")
      .filter({ hasText: new RegExp(`^${label}$`) })
      .first(),
  ).toBeVisible();
}

/** Navigates to /executions filtered by workflow type. */
async function openExecutionsSearch(
  page: import("@playwright/test").Page,
  workflowType: string,
) {
  await page.goto(
    `/executions?workflowType=${encodeURIComponent(workflowType)}`,
  );
  await page.waitForLoadState("networkidle");
}

/**
 * Starts a SET_VARIABLE workflow, waits until it completes, then waits until
 * the executions search index surfaces its NavLink (Postgres FTS can lag).
 */
async function startAndFindExecution(page: import("@playwright/test").Page) {
  const workflowId = (await startWorkflow(WF_NAME, { value: "test" })).trim();
  startedWorkflowIds.push(workflowId);

  const wf = await waitForWorkflow(workflowId, { timeoutMs: 30_000 });
  expect(wf.status).toBe("COMPLETED");

  await openExecutionsSearch(page, WF_NAME);

  // ResultsTable renders a NavLink with the full workflow ID — wait for index lag.
  const link = page.getByRole("link", { name: workflowId });
  await expect(link).toBeVisible({ timeout: SEARCH_INDEX_TIMEOUT_MS });
  return { workflowId, link };
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test("started workflow execution appears in the executions search", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await expect(page.getByText(WF_NAME).first()).toBeVisible();
  await expect(page.getByRole("link", { name: workflowId })).toBeVisible();
});

test("execution row shows the workflow ID", async ({ page }) => {
  const { workflowId } = await startAndFindExecution(page);

  await expect(page.getByRole("link", { name: workflowId })).toBeVisible();
});

test("clicking an execution row opens the execution detail page", async ({
  page,
}) => {
  const { workflowId, link } = await startAndFindExecution(page);

  await link.click();

  await expect(page).toHaveURL(new RegExp(`/execution/${workflowId}`), {
    timeout: 15_000,
  });
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(WF_NAME).first()).toBeVisible();
  await expectExecutionStatusChip(page, "COMPLETED");
  await expect(page.getByText(workflowId).first()).toBeVisible();
  await expect(page.getByText("set_var_ref")).toBeVisible();
});

test("executions page renders the search form", async ({ page }) => {
  await page.goto("/executions");
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content input").first()).toBeVisible();
});
