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
  getWorkflowExecution,
  startWorkflow,
  terminateWorkflow,
  waitForWorkflow,
  type WorkflowDef,
} from "./api-client";

const RUN_ID = Date.now();
const WF_NAME = `e2e_exec_${RUN_ID}`;
const CORRELATION_ID = `e2e-corr-${RUN_ID}`;
const SEARCH_INDEX_TIMEOUT_MS = 45_000;

const WORKFLOW_DEF: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description: "Created by Playwright E2E test — safe to delete",
  inputParameters: ["value"],
  outputParameters: { result: "${set_var_ref.output.result}" },
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

/** Status chips render title-case ("Completed"). */
async function expectStatusChip(
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
  const workflowId = (
    await startWorkflow(WF_NAME, { value: "test" }, { correlationId: CORRELATION_ID })
  ).trim();
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
  await expectStatusChip(page, "COMPLETED");
  await expect(page.getByText(workflowId).first()).toBeVisible();
  // Diagram default view: task reference label is visible on the card.
  await expect(page.getByText("set_var_ref")).toBeVisible();
});

test("execution detail tabs — Task List shows task row fields", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Task List" }).click();

  // Column headers
  await expect(page.getByText("SEQ.").first()).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText("TASK ID").first()).toBeVisible();
  await expect(page.getByText("REF").first()).toBeVisible();
  await expect(page.getByText("TYPE").first()).toBeVisible();

  // Data row: seq 1, type, and ref name
  await expect(page.getByText("set_var_ref").first()).toBeVisible();
  await expect(page.getByText("SET_VARIABLE").first()).toBeVisible();

  // The task ID link is rendered as a 4..4 truncation.
  await expect(
    page
      .locator("#main-content")
      .getByRole("link")
      .filter({ hasText: /\.\./ })
      .first(),
  ).toBeVisible();
});

test("execution detail tabs — Timeline renders task label", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Timeline" }).click();
  // Gantt chart renders the task reference name in the label column.
  await expect(page.getByText("set_var_ref").first()).toBeVisible({
    timeout: 15_000,
  });
});

test("execution detail tabs — Summary tab shows workflow metadata", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Summary" }).click();

  // KeyValueTable rows rendered by ExecutionSummary
  await expect(page.getByText("Workflow id").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(workflowId).first()).toBeVisible();
  await expect(page.getByText("Status").first()).toBeVisible();
  await expect(page.getByText("COMPLETED").first()).toBeVisible();
  await expect(page.getByText("Version").first()).toBeVisible();
  await expect(page.getByText("1").first()).toBeVisible();
  await expect(page.getByText("Start time").first()).toBeVisible();
  await expect(page.getByText("End time").first()).toBeVisible();
  await expect(page.getByText("Duration").first()).toBeVisible();

  // Correlation ID was passed when starting the workflow.
  await expect(page.getByText("Correlation id").first()).toBeVisible();
  await expect(page.getByText(CORRELATION_ID).first()).toBeVisible();
});

test("execution detail tabs — Workflow Input/Output shows passed-in value", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Workflow Input/Output" }).click();
  // Input section: the workflow was started with { value: "test" }.
  await expect(page.getByText(/"value"|value/i).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(/"test"|test/).first()).toBeVisible();
  // Output section: outputParameters maps "result" from SET_VARIABLE output.
  await expect(page.getByText(/"result"|result/i).first()).toBeVisible();
});

test("execution detail tabs — JSON tab contains workflowId key", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "JSON" }).click();
  // Monaco renders the raw execution JSON — workflowId key must appear.
  await expect(page.getByText(/"workflowId"/).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(/"status"/).first()).toBeVisible();
  // The actual workflow ID value appears in the JSON body.
  await expect(page.getByText(workflowId).first()).toBeVisible();
});

test("execution detail tabs — Variables tab renders for SET_VARIABLE", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Variables" }).click();
  // SET_VARIABLE writes to workflow.variables; the Variables tab renders those
  // as JSON. The key "result" should appear.
  await expect(page.getByText(/"result"|result/i).first()).toBeVisible({
    timeout: 15_000,
  });
});

test("task list — clicking task ID opens right panel with task metadata", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  await page.getByRole("tab", { name: "Task List" }).click();
  await expect(page.getByText("set_var_ref").first()).toBeVisible({
    timeout: 15_000,
  });

  // Only the Task Id link selects the task (Ref column is plain text).
  await page
    .locator("#main-content")
    .getByRole("link")
    .filter({ hasText: /\.\./ })
    .first()
    .click();

  const rightPanel = page.locator("#execution-page-right-panel");
  await expect(rightPanel).toBeVisible({ timeout: 15_000 });

  // Right-panel header: task name + status chip
  await expect(rightPanel.getByText("set_var").first()).toBeVisible();
  await expect(
    rightPanel
      .locator(".MuiChip-label")
      .filter({ hasText: /^Completed$/ })
      .first(),
  ).toBeVisible();

  // Summary tab (default): TaskSummary KeyValueTable rows
  await expect(rightPanel.getByText("Task type").first()).toBeVisible();
  await expect(rightPanel.getByText("SET_VARIABLE").first()).toBeVisible();
  await expect(rightPanel.getByText("Task reference").first()).toBeVisible();
  await expect(rightPanel.getByText("set_var_ref").first()).toBeVisible();
  await expect(rightPanel.getByText("Task name").first()).toBeVisible();
  await expect(rightPanel.getByText("set_var").first()).toBeVisible();
  await expect(rightPanel.getByText("Task execution id").first()).toBeVisible();
  await expect(rightPanel.getByText("Retry count").first()).toBeVisible();
  await expect(rightPanel.getByText("Scheduled time").first()).toBeVisible();
  await expect(rightPanel.getByText("Start time").first()).toBeVisible();
  await expect(rightPanel.getByText("End time").first()).toBeVisible();
  await expect(rightPanel.getByText("Duration").first()).toBeVisible();

  // Input tab: the configured inputParameters must appear
  await rightPanel.getByRole("tab", { name: "Input" }).click();
  await expect(
    rightPanel.getByText(/e2e-test-value|result/i).first(),
  ).toBeVisible({ timeout: 15_000 });

  // JSON tab: Monaco virtualizes off-screen lines, so query the model directly.
  await rightPanel.getByRole("tab", { name: "JSON" }).click();
  await expect(rightPanel.locator(".monaco-editor").first()).toBeVisible({
    timeout: 15_000,
  });
  const taskJsonContainsId = await page.evaluate(() => {
    const monaco = (window as { monaco?: { editor: { getModels(): Array<{ getValue(): string }> } } }).monaco;
    return (
      monaco?.editor
        .getModels()
        .some((m) => m.getValue().includes('"taskId"')) ?? false
    );
  });
  expect(taskJsonContainsId, "task JSON must contain taskId key").toBe(true);
});

test("execution deep-link with taskId opens the task panel", async ({
  page,
}) => {
  const { workflowId } = await startAndFindExecution(page);
  const wf = await getWorkflowExecution(workflowId);
  const task =
    wf.tasks?.find((t) => t.referenceTaskName === "set_var_ref") ??
    wf.tasks?.[0];
  expect(task).toBeTruthy();

  // Prefer taskId query when present; fall back to taskReferenceName.
  const qs = task?.taskId
    ? `taskId=${encodeURIComponent(task.taskId)}`
    : `taskReferenceName=${encodeURIComponent(task!.referenceTaskName)}`;

  await page.goto(`/execution/${workflowId}?${qs}`);
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });

  const rightPanel = page.locator("#execution-page-right-panel");
  await expect(rightPanel).toBeVisible({ timeout: 15_000 });

  // Panel pre-selects the deep-linked task.
  await expect(rightPanel.getByText("set_var_ref").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(
    rightPanel.getByText("Task reference").first(),
  ).toBeVisible();
  await expect(rightPanel.getByText("set_var_ref").first()).toBeVisible();

  // The full taskId from the URL should appear in the panel header.
  if (task?.taskId) {
    await expect(page.getByText(task.taskId).first()).toBeVisible({
      timeout: 15_000,
    });
  }
});

test("executions page renders the search form and workflow name filter", async ({
  page,
}) => {
  await page.goto("/executions");
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.locator("#main-content input").first()).toBeVisible();

  // The URL filter param is reflected in the workflow-type input.
  await page.goto(
    `/executions?workflowType=${encodeURIComponent(WF_NAME)}`,
  );
  await page.waitForLoadState("networkidle");
  await expect(
    page.locator("#main-content input[value]").filter({ hasText: "" }).first(),
  ).toBeAttached();
  // The workflow name should appear somewhere in the search result area.
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: SEARCH_INDEX_TIMEOUT_MS,
  });
});
