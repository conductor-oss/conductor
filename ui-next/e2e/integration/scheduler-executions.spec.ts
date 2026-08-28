/**
 * Integration tests — Scheduler Executions
 *
 * Creates a schedule with a fast seconds-level cron, waits until the scheduler
 * fires at least one EXECUTED run (API poll), then verifies /schedulerExecs
 * shows the execution and links to the started workflow.
 *
 * Docker defaults include ~15s scheduler initialDelayMs — timeouts account for
 * that plus cron interval and archival lag.
 */

import { expect, test } from "../coverage-fixture";
import {
  createSchedule,
  createWorkflowDef,
  deleteSchedule,
  deleteWorkflowDef,
  pauseSchedule,
  terminateWorkflow,
  waitForSchedulerExecution,
  waitForWorkflow,
  type WorkflowDef,
  type WorkflowSchedule,
} from "./api-client";
import {
  expectMainContentScreenshot,
  waitForExecutionDiagramReady,
} from "./helpers";

const RUN_ID = Date.now();
const WF_NAME = `e2e_sched_exec_wf_${RUN_ID}`;
const SCHED_NAME = `e2e_sched_exec_${RUN_ID}`;
/** initialDelay (~15s) + cron + archival — keep generous CI headroom. */
const SCHEDULER_FIRE_TIMEOUT_MS = 120_000;

const WORKFLOW: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description: "Scheduler execution target — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  tasks: [
    {
      name: "set_var",
      taskReferenceName: "set_var_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        fromScheduler: true,
        note: "e2e",
      },
    },
  ],
};

const SCHEDULE: WorkflowSchedule = {
  name: SCHED_NAME,
  description: "Fast cron schedule for Playwright e2e — safe to delete",
  // Every 10 seconds (6-field Quartz).
  cronExpression: "*/10 * * * * *",
  zoneId: "UTC",
  paused: false,
  runCatchupScheduleInstances: false,
  startWorkflowRequest: {
    name: WF_NAME,
    version: 1,
    input: { source: "scheduler-e2e" },
  },
};

const startedWorkflowIds: string[] = [];

test.beforeAll(async () => {
  await createWorkflowDef(WORKFLOW);
  await createSchedule(SCHEDULE);
});

test.afterAll(async () => {
  // Stop further fires before deleting the definition.
  await pauseSchedule(SCHED_NAME).catch(() => {});
  await deleteSchedule(SCHED_NAME).catch(() => {});
  await Promise.allSettled(
    startedWorkflowIds.map((id) => terminateWorkflow(id)),
  );
  await deleteWorkflowDef(WF_NAME).catch(() => {});
});

test.describe.configure({ mode: "serial" });

test("scheduler fires the schedule and records an EXECUTED execution", async () => {
  test.setTimeout(SCHEDULER_FIRE_TIMEOUT_MS + 30_000);

  const execution = await waitForSchedulerExecution(SCHED_NAME, {
    timeoutMs: SCHEDULER_FIRE_TIMEOUT_MS,
    state: "EXECUTED",
  });

  expect(execution.scheduleName).toBe(SCHED_NAME);
  expect(execution.workflowName).toBe(WF_NAME);
  expect(execution.state).toBe("EXECUTED");
  expect(execution.executionId).toBeTruthy();
  expect(execution.workflowId).toBeTruthy();

  startedWorkflowIds.push(execution.workflowId!);

  // The started workflow should complete without a worker (SET_VARIABLE).
  const wf = await waitForWorkflow(execution.workflowId!, {
    timeoutMs: 30_000,
  });
  expect(wf.status).toBe("COMPLETED");
});

test("scheduler execution appears in /schedulerExecs search", async ({
  page,
}) => {
  test.setTimeout(SCHEDULER_FIRE_TIMEOUT_MS + 60_000);

  const execution = await waitForSchedulerExecution(SCHED_NAME, {
    timeoutMs: SCHEDULER_FIRE_TIMEOUT_MS,
    state: "EXECUTED",
  });
  if (execution.workflowId) {
    startedWorkflowIds.push(execution.workflowId);
  }

  // URL scheduleName filter seeds the query; Search still refreshes results.
  await page.goto(
    `/schedulerExecs?scheduleName=${encodeURIComponent(SCHED_NAME)}`,
  );
  await page.waitForLoadState("networkidle");

  await page.getByRole("button", { name: "Search", exact: true }).click();

  await expect(page.getByText(SCHED_NAME).first()).toBeVisible({
    timeout: 30_000,
  });
  await expect(page.getByText(execution.executionId).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(WF_NAME).first()).toBeVisible();
  await expect(
    page
      .locator(".MuiChip-label")
      .filter({ hasText: /^Executed$/i })
      .first(),
  ).toBeVisible({ timeout: 15_000 });

  await expectMainContentScreenshot(page, "scheduler-executions-search.png");

  if (execution.workflowId) {
    const wfLink = page.getByRole("link", { name: execution.workflowId });
    await expect(wfLink).toBeVisible({ timeout: 15_000 });
    await wfLink.click();
    await expect(page).toHaveURL(
      new RegExp(`/execution/${execution.workflowId}`),
      { timeout: 15_000 },
    );
    await page.waitForLoadState("networkidle");
    await expect(page.getByText(WF_NAME).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText("set_var_ref").first()).toBeVisible({
      timeout: 15_000,
    });
    // Refs/DOM styles can appear before the SVG foreignObject layer is painted.
    // Opening a task forces a real layout paint (same pattern as other execution
    // snapshot tests). Escape may not dismiss the panel — mask it instead.
    await waitForExecutionDiagramReady(page);
    await page.getByText("set_var_ref").first().click();
    await expect(page.locator("#execution-page-right-panel")).toBeVisible({
      timeout: 15_000,
    });
    await page.keyboard.press("Escape");

    await expectMainContentScreenshot(
      page,
      "scheduler-started-workflow-execution.png",
      {
        mask: [
          page.locator("#execution-page-right-panel"),
          page.getByRole("heading").filter({ hasText: /e2e_/ }),
        ],
      },
    );
  }
});

test("definitions list links to scheduler executions filtered by schedule", async ({
  page,
}) => {
  await page.goto("/scheduleDef");
  await page.waitForLoadState("networkidle");
  await page.getByPlaceholder("Search scheduler definitions").fill(SCHED_NAME);
  await expect(page.locator("#main-content").getByText(SCHED_NAME)).toBeVisible(
    { timeout: 15_000 },
  );

  const row = page.locator("#main-content").getByRole("row").filter({
    hasText: SCHED_NAME,
  });
  await row.getByRole("link", { name: "Scheduler query" }).click();

  await expect(page).toHaveURL(/\/schedulerExecs/, { timeout: 15_000 });
  await expect(page).toHaveURL(new RegExp(`scheduleName=${SCHED_NAME}`));
});
