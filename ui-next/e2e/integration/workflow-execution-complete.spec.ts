/**
 * Integration tests — Multi-task workflow create + execute → COMPLETED
 *
 * Registers a workflow that exercises several built-in system task types
 * (no SIMPLE workers required), starts an execution with known input, and
 * asserts both the API result and the execution UI show a successful run.
 *
 * Task topology (region = "us"):
 *
 *   capture_input          SET_VARIABLE
 *     → enrich_order       INLINE (tax / total)
 *     → transform_payload  JSON_JQ_TRANSFORM
 *     → route_by_region    SWITCH
 *           ├── "us"  → set_us_route     SET_VARIABLE
 *           ├── "eu"  → set_eu_route     SET_VARIABLE
 *           └── default → set_intl_route SET_VARIABLE
 *     → parallel_enrich    FORK_JOIN
 *           ├── fork_a_label            SET_VARIABLE
 *           └── fork_b_compute          INLINE
 *     → join_parallel      JOIN
 *     → once_loop          DO_WHILE (single iteration)
 *           └── loop_body               SET_VARIABLE
 *     → call_child         SUB_WORKFLOW (tiny SET_VARIABLE child)
 *     → finalize           SET_VARIABLE
 */

import { expect, test } from "../coverage-fixture";
import {
  createTaskDef,
  createWorkflowDef,
  deleteTaskDef,
  deleteWorkflowDef,
  getWorkflowDef,
  getWorkflowExecution,
  startWorkflow,
  terminateWorkflow,
  updateTask,
  waitForWorkflow,
  type WorkflowDef,
  type WorkflowExecution,
  type WorkflowTaskExecution,
} from "./api-client";
import {
  expectMainContentScreenshot,
  fitDiagramToScreen,
  setDiagramSnapshotViewport,
  waitForExecutionDiagramReady,
} from "./helpers";

const RUN_ID = Date.now();
const CHILD_WF_NAME = `e2e_multi_child_${RUN_ID}`;
const PARENT_WF_NAME = `e2e_multi_parent_${RUN_ID}`;
const SEARCH_INDEX_TIMEOUT_MS = 45_000;
const EXECUTION_TIMEOUT_MS = 60_000;

// Extra task-type workflow names
const EVENT_WF_NAME = `e2e_event_wf_${RUN_ID}`;
/** A unique conductor sink per run so events don't bleed between test runs. */
const EVENT_SINK = `conductor:e2e_evt_${RUN_ID}`;
/** Task type for the SIMPLE worker test — must be registered as a TaskDef. */
const SIMPLE_TASK_TYPE = `e2e_simple_${RUN_ID}`;
const SIMPLE_WF_NAME = `e2e_simple_wf_${RUN_ID}`;
const LAUNCH_WF_NAME = `e2e_launch_wf_${RUN_ID}`;

const ORDER_INPUT = {
  orderId: "ORD-42",
  amount: 100,
  region: "us",
} as const;

/**
 * Task refs that must appear when the "us" SWITCH branch is taken.
 * DO_WHILE body tasks are stored as `<ref>__<iteration>` at runtime.
 */
const EXPECTED_COMPLETED_REFS = [
  "capture_input_ref",
  "enrich_order_ref",
  "transform_payload_ref",
  "route_by_region_ref",
  "set_us_route_ref",
  "parallel_enrich_ref",
  "fork_a_label_ref",
  "fork_b_compute_ref",
  "join_parallel_ref",
  "once_loop_ref",
  "loop_body_ref__1",
  "call_child_ref",
  "finalize_ref",
] as const;

/** SWITCH branches that must NOT run for region=us. */
const SKIPPED_SWITCH_REFS = ["set_eu_route_ref", "set_intl_route_ref"] as const;

const CHILD_WORKFLOW: WorkflowDef = {
  name: CHILD_WF_NAME,
  version: 1,
  description: "Child workflow for multi-task e2e — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  tasks: [
    {
      name: "child_mark",
      taskReferenceName: "child_mark_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        childAck: "ok-${workflow.input.parentOrderId}",
      },
    },
  ],
  inputParameters: ["parentOrderId"],
  outputParameters: {
    childAck: "${workflow.variables.childAck}",
  },
};

const PARENT_WORKFLOW: WorkflowDef = {
  name: PARENT_WF_NAME,
  version: 1,
  description: "Multi-task system workflow for Playwright E2E — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  inputParameters: ["orderId", "amount", "region"],
  outputParameters: {
    orderId: "${workflow.variables.orderId}",
    route: "${workflow.variables.route}",
    total: "${enrich_order_ref.output.result.total}",
    tax: "${enrich_order_ref.output.result.tax}",
    jqOrderId: "${transform_payload_ref.output.result.orderId}",
    forkA: "${fork_a_label_ref.output}",
    forkB: "${fork_b_compute_ref.output.result}",
    loopPass: "${workflow.variables.loopPass}",
    childAck: "${call_child_ref.output.childAck}",
    finalized: "${workflow.variables.finalized}",
  },
  tasks: [
    {
      name: "capture_input",
      taskReferenceName: "capture_input_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        orderId: "${workflow.input.orderId}",
        amount: "${workflow.input.amount}",
        region: "${workflow.input.region}",
      },
    },
    {
      name: "enrich_order",
      taskReferenceName: "enrich_order_ref",
      type: "INLINE",
      inputParameters: {
        evaluatorType: "graaljs",
        expression:
          "(function () { return { tax: $.amount * 0.1, total: $.amount * 1.1 }; })();",
        amount: "${workflow.input.amount}",
      },
    },
    {
      name: "transform_payload",
      taskReferenceName: "transform_payload_ref",
      type: "JSON_JQ_TRANSFORM",
      inputParameters: {
        orderId: "${workflow.input.orderId}",
        amount: "${workflow.input.amount}",
        tax: "${enrich_order_ref.output.result.tax}",
        total: "${enrich_order_ref.output.result.total}",
        queryExpression:
          '{ orderId: .orderId, amount: .amount, tax: .tax, total: .total, tagged: (.orderId + "-priced") }',
      },
    },
    {
      name: "route_by_region",
      taskReferenceName: "route_by_region_ref",
      type: "SWITCH",
      evaluatorType: "value-param",
      expression: "switchCaseValue",
      inputParameters: {
        switchCaseValue: "${workflow.input.region}",
      },
      decisionCases: {
        us: [
          {
            name: "set_us_route",
            taskReferenceName: "set_us_route_ref",
            type: "SET_VARIABLE",
            inputParameters: { route: "domestic" },
          },
        ],
        eu: [
          {
            name: "set_eu_route",
            taskReferenceName: "set_eu_route_ref",
            type: "SET_VARIABLE",
            inputParameters: { route: "europe" },
          },
        ],
      },
      defaultCase: [
        {
          name: "set_intl_route",
          taskReferenceName: "set_intl_route_ref",
          type: "SET_VARIABLE",
          inputParameters: { route: "international" },
        },
      ],
    },
    {
      name: "parallel_enrich",
      taskReferenceName: "parallel_enrich_ref",
      type: "FORK_JOIN",
      forkTasks: [
        [
          {
            name: "fork_a_label",
            taskReferenceName: "fork_a_label_ref",
            type: "SET_VARIABLE",
            inputParameters: { forkLabel: "A" },
          },
        ],
        [
          {
            name: "fork_b_compute",
            taskReferenceName: "fork_b_compute_ref",
            type: "INLINE",
            inputParameters: {
              evaluatorType: "graaljs",
              expression: '(function () { return "branch-b"; })();',
            },
          },
        ],
      ],
    },
    {
      name: "join_parallel",
      taskReferenceName: "join_parallel_ref",
      type: "JOIN",
      joinOn: ["fork_a_label_ref", "fork_b_compute_ref"],
    },
    {
      name: "once_loop",
      taskReferenceName: "once_loop_ref",
      type: "DO_WHILE",
      // First body always runs; condition false → stop after one iteration.
      loopCondition: "false",
      loopOver: [
        {
          name: "loop_body",
          taskReferenceName: "loop_body_ref",
          type: "SET_VARIABLE",
          inputParameters: { loopPass: true },
        },
      ],
    },
    {
      name: "call_child",
      taskReferenceName: "call_child_ref",
      type: "SUB_WORKFLOW",
      subWorkflowParam: {
        name: CHILD_WF_NAME,
        version: 1,
      },
      inputParameters: {
        parentOrderId: "${workflow.input.orderId}",
      },
    },
    {
      name: "finalize",
      taskReferenceName: "finalize_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        finalized: true,
        summary:
          "${workflow.input.orderId}:${workflow.variables.route}:${enrich_order_ref.output.result.total}",
      },
    },
  ],
};

const startedWorkflowIds: string[] = [];

test.beforeAll(async () => {
  // Child must exist before the parent references it.
  await createWorkflowDef(CHILD_WORKFLOW);
  await createWorkflowDef(PARENT_WORKFLOW);
});

test.afterAll(async () => {
  await Promise.allSettled(
    startedWorkflowIds.map((id) => terminateWorkflow(id)),
  );
  await deleteWorkflowDef(PARENT_WF_NAME).catch(() => {});
  await deleteWorkflowDef(CHILD_WF_NAME).catch(() => {});
});

// ── Helpers ────────────────────────────────────────────────────────────────────

function taskByRef(
  wf: WorkflowExecution,
  ref: string,
): WorkflowTaskExecution | undefined {
  return wf.tasks?.find((t) => t.referenceTaskName === ref);
}

/** Definition diagram uses bare refs; executions use `ref__N` for DO_WHILE bodies. */
function expectTaskRefVisible(
  page: import("@playwright/test").Page,
  ref: string,
) {
  // Prefer exact text, but DO_WHILE iteration refs may appear with or without __N
  // depending on the view — match the prefix either way.
  const bare = ref.replace(/__\d+$/, "");
  return expect(
    page.getByText(ref).or(page.getByText(bare)).first(),
  ).toBeVisible({ timeout: 15_000 });
}

/** Status chips render title case ("Completed"). */
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
  ).toBeVisible({ timeout: 15_000 });
}

/**
 * Open a task on the execution diagram and assert the Output tab shows
 * `expected` text (diagram nodes themselves do not render task output).
 */
async function expectTaskOutputVisible(
  page: import("@playwright/test").Page,
  taskRefName: string,
  expected: RegExp | string,
) {
  await page.getByText(taskRefName).first().click();
  const rightPanel = page.locator("#execution-page-right-panel");
  await expect(rightPanel).toBeVisible({ timeout: 15_000 });
  await rightPanel.getByRole("tab", { name: "Output" }).click();
  await expect(rightPanel.getByText(expected).first()).toBeVisible({
    timeout: 15_000,
  });
}

/** Start parent workflow, wait until COMPLETED, return API payload. */
async function runParentToCompletion(
  input: Record<string, unknown> = { ...ORDER_INPUT },
) {
  const workflowId = await startWorkflow(PARENT_WF_NAME, input);
  startedWorkflowIds.push(workflowId);

  const wf = await waitForWorkflow(workflowId, {
    timeoutMs: EXECUTION_TIMEOUT_MS,
  });

  expect(
    wf.status,
    `Expected COMPLETED but got ${wf.status} for ${workflowId}`,
  ).toBe("COMPLETED");

  return { workflowId, wf };
}

function assertApiExecution(wf: WorkflowExecution) {
  expect(wf.workflowName ?? wf.workflowType).toBe(PARENT_WF_NAME);
  expect(wf.status).toBe("COMPLETED");
  expect(wf.tasks?.length).toBeGreaterThan(0);

  for (const ref of EXPECTED_COMPLETED_REFS) {
    const task = taskByRef(wf, ref);
    expect(task, `missing task ${ref}`).toBeTruthy();
    expect(task!.status, `${ref} status`).toBe("COMPLETED");
  }

  for (const ref of SKIPPED_SWITCH_REFS) {
    expect(
      taskByRef(wf, ref),
      `${ref} should not run for region=us`,
    ).toBeUndefined();
  }

  // INLINE enrich: amount 100 → tax 10, total 110 (GraalJS float math)
  const enrich = taskByRef(wf, "enrich_order_ref");
  expect(enrich?.taskType).toBe("INLINE");
  const enrichResult = enrich?.outputData?.result as
    | { tax?: number; total?: number }
    | undefined;
  expect(enrichResult?.tax).toBeCloseTo(10);
  expect(enrichResult?.total).toBeCloseTo(110);

  // JQ transform tagged order id
  const jq = taskByRef(wf, "transform_payload_ref");
  expect(jq?.taskType).toBe("JSON_JQ_TRANSFORM");
  const jqResult = jq?.outputData?.result as
    | { orderId?: string; tagged?: string; total?: number }
    | undefined;
  expect(jqResult?.orderId).toBe(ORDER_INPUT.orderId);
  expect(jqResult?.tagged).toBe(`${ORDER_INPUT.orderId}-priced`);
  expect(jqResult?.total).toBeCloseTo(110);

  // SWITCH → us branch
  expect(taskByRef(wf, "route_by_region_ref")?.taskType).toBe("SWITCH");
  expect(wf.variables?.route).toBe("domestic");

  // FORK branches — runtime taskType is "FORK" even when the def type is FORK_JOIN
  expect(taskByRef(wf, "parallel_enrich_ref")?.taskType).toMatch(
    /^FORK(_JOIN)?$/,
  );
  expect(taskByRef(wf, "join_parallel_ref")?.taskType).toBe("JOIN");
  expect(wf.variables?.forkLabel).toBe("A");
  expect(taskByRef(wf, "fork_b_compute_ref")?.outputData?.result).toBe(
    "branch-b",
  );

  // DO_WHILE single pass
  expect(taskByRef(wf, "once_loop_ref")?.taskType).toBe("DO_WHILE");
  expect(wf.variables?.loopPass).toBe(true);

  // SUB_WORKFLOW child output
  const child = taskByRef(wf, "call_child_ref");
  expect(child?.taskType).toBe("SUB_WORKFLOW");
  expect(child?.outputData?.childAck).toBe(`ok-${ORDER_INPUT.orderId}`);

  // Finalize + workflow-level outputParameters
  expect(wf.variables?.finalized).toBe(true);
  expect(wf.output?.orderId).toBe(ORDER_INPUT.orderId);
  expect(wf.output?.route).toBe("domestic");
  expect(wf.output?.total).toBeCloseTo(110);
  expect(wf.output?.tax).toBeCloseTo(10);
  expect(wf.output?.jqOrderId).toBe(ORDER_INPUT.orderId);
  expect(wf.output?.forkB).toBe("branch-b");
  expect(wf.output?.loopPass).toBe(true);
  expect(wf.output?.childAck).toBe(`ok-${ORDER_INPUT.orderId}`);
  expect(wf.output?.finalized).toBe(true);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe.configure({ mode: "serial" });

test("multi-task parent and child definitions appear in the list", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(PARENT_WF_NAME)).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText(CHILD_WF_NAME)).toBeVisible();
});

test("definition editor shows every top-level and nested task ref", async ({
  page,
}) => {
  test.setTimeout(60_000);

  // API: confirm the registered definition still contains the full topology
  // (Monaco only mounts visible lines in the DOM, so toContainText cannot see
  // below-the-fold JSON).
  const saved = await getWorkflowDef(PARENT_WF_NAME, 1);
  expect(saved.name).toBe(PARENT_WF_NAME);
  expect(saved.tasks.map((t) => t.taskReferenceName)).toEqual([
    "capture_input_ref",
    "enrich_order_ref",
    "transform_payload_ref",
    "route_by_region_ref",
    "parallel_enrich_ref",
    "join_parallel_ref",
    "once_loop_ref",
    "call_child_ref",
    "finalize_ref",
  ]);
  expect(saved.tasks.map((t) => t.type)).toEqual([
    "SET_VARIABLE",
    "INLINE",
    "JSON_JQ_TRANSFORM",
    "SWITCH",
    "FORK_JOIN",
    "JOIN",
    "DO_WHILE",
    "SUB_WORKFLOW",
    "SET_VARIABLE",
  ]);

  const switchTask = saved.tasks.find(
    (t) => t.taskReferenceName === "route_by_region_ref",
  );
  expect(Object.keys(switchTask?.decisionCases ?? {}).sort()).toEqual([
    "eu",
    "us",
  ]);
  expect(switchTask?.defaultCase?.[0]?.taskReferenceName).toBe(
    "set_intl_route_ref",
  );

  const forkTask = saved.tasks.find(
    (t) => t.taskReferenceName === "parallel_enrich_ref",
  );
  expect(forkTask?.forkTasks?.flat().map((t) => t.taskReferenceName)).toEqual([
    "fork_a_label_ref",
    "fork_b_compute_ref",
  ]);

  const loopTask = saved.tasks.find(
    (t) => t.taskReferenceName === "once_loop_ref",
  );
  expect(loopTask?.loopOver?.[0]?.taskReferenceName).toBe("loop_body_ref");

  const subTask = saved.tasks.find(
    (t) => t.taskReferenceName === "call_child_ref",
  );
  expect(subTask?.subWorkflowParam?.name).toBe(CHILD_WF_NAME);

  await page.goto(`/workflowDef/${PARENT_WF_NAME}/1`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#workflow-name-display")).toBeVisible();
  await expect(page.getByText(PARENT_WF_NAME).first()).toBeVisible();

  // Diagram viewport only fits the first few nodes.
  for (const ref of [
    "capture_input_ref",
    "enrich_order_ref",
    "transform_payload_ref",
  ]) {
    await expect(page.getByText(ref).first()).toBeVisible({ timeout: 15_000 });
  }

  // Spot-check task forms for a few distinct types on the diagram.
  await page.getByText("enrich_order_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("INLINE", { exact: true }),
  ).toBeVisible();

  await page.getByText("transform_payload_ref").first().click();
  await expect(
    page
      .locator("#maybe-task-form")
      .getByText("JSON_JQ_TRANSFORM", { exact: true }),
  ).toBeVisible();

  await page.getByText("capture_input_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("SET_VARIABLE", { exact: true }),
  ).toBeVisible();

  await setDiagramSnapshotViewport(page);
  await fitDiagramToScreen(page);
  // Lower nodes only enter the frame after fit-to-screen at the larger viewport.
  await expect(page.getByText("finalize_ref").first()).toBeVisible({
    timeout: 15_000,
  });

  // Complex control-flow forms (coverage targets for SWITCH / DO_WHILE / etc.).
  // After fit, lower-node labels can be tiny / partially clipped — scroll +
  // force click keeps the tour reliable. DO_WHILE cards show the task *name*
  // (not reference) on the diagram shell.
  const clickDiagramLabel = async (label: string) => {
    const node = page.getByText(label, { exact: true }).first();
    await node.scrollIntoViewIfNeeded();
    await node.click({ force: true });
  };

  await clickDiagramLabel("route_by_region_ref");
  await expect(
    page.locator("#maybe-task-form").getByText("SWITCH", { exact: true }),
  ).toBeVisible({ timeout: 15_000 });

  await clickDiagramLabel("parallel_enrich_ref");
  await expect(
    page.locator("#maybe-task-form").getByText("FORK_JOIN", { exact: true }),
  ).toBeVisible({ timeout: 15_000 });

  await clickDiagramLabel("join_parallel_ref");
  await expect(
    page.locator("#maybe-task-form").getByText("JOIN", { exact: true }),
  ).toBeVisible({ timeout: 15_000 });

  await fitDiagramToScreen(page);
  await clickDiagramLabel("once_loop");
  await expect(
    page.locator("#maybe-task-form").getByText("DO_WHILE", { exact: true }),
  ).toBeVisible({ timeout: 15_000 });

  await clickDiagramLabel("call_child_ref");
  await expect(
    page.locator("#maybe-task-form").getByText("SUB_WORKFLOW", { exact: true }),
  ).toBeVisible({ timeout: 15_000 });

  // Workflow properties tab (WorkflowPropertiesFormTab).
  await page.getByRole("tab", { name: "Workflow" }).click();
  await expect(page.locator("#workflow-properties-form")).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.locator("#workflow-name-field")).toBeVisible();
  await expect(page.locator("#workflow-description-field")).toBeVisible();

  // Return to Task tab so the snapshot still shows the diagram + task panel.
  await page.getByRole("tab", { name: "Task" }).click();
  await page.getByText("capture_input_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("SET_VARIABLE", { exact: true }),
  ).toBeVisible();

  await expectMainContentScreenshot(
    page,
    "multi-task-workflow-definition.png",
    {
      mask: [
        page.locator("#workflow-name-display"),
        page.locator("#maybe-task-form"),
      ],
    },
  );
});

test("executing the multi-task workflow completes successfully (API)", async () => {
  test.setTimeout(EXECUTION_TIMEOUT_MS + 30_000);

  const { wf } = await runParentToCompletion();
  assertApiExecution(wf);
});

test("completed execution detail page shows status, tasks, and outputs", async ({
  page,
}) => {
  test.setTimeout(EXECUTION_TIMEOUT_MS + 60_000);

  const { workflowId, wf } = await runParentToCompletion();
  assertApiExecution(wf);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(PARENT_WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(workflowId).first()).toBeVisible();
  await expectExecutionStatusChip(page, "COMPLETED");

  // Early diagram nodes are in view; later nodes may be below the fold.
  // Full task/output correctness (including which SWITCH branch ran) is
  // covered by assertApiExecution above.
  for (const ref of [
    "capture_input_ref",
    "enrich_order_ref",
    "transform_payload_ref",
  ]) {
    await expectTaskRefVisible(page, ref);
  }

  await expectTaskOutputVisible(page, "enrich_order_ref", /110|10/);
  await expectTaskOutputVisible(page, "transform_payload_ref", /ORD-42-priced/);

  // Close the task panel so the diagram fills the snapshot cleanly.
  await waitForExecutionDiagramReady(page);
  await page.keyboard.press("Escape");
  await setDiagramSnapshotViewport(page);
  await fitDiagramToScreen(page);
  await expect(page.getByText("finalize_ref").first()).toBeVisible({
    timeout: 15_000,
  });
  await expectMainContentScreenshot(page, "multi-task-workflow-execution.png", {
    mask: [
      page.locator("#execution-page-right-panel"),
      page.getByRole("heading").filter({ hasText: /e2e_/ }),
    ],
  });
});

test("completed multi-task execution appears in executions search", async ({
  page,
}) => {
  test.setTimeout(EXECUTION_TIMEOUT_MS + SEARCH_INDEX_TIMEOUT_MS + 30_000);

  const { workflowId } = await runParentToCompletion();

  await page.goto(
    `/executions?workflowType=${encodeURIComponent(PARENT_WF_NAME)}`,
  );
  await page.waitForLoadState("networkidle");

  await expect(page.getByRole("link", { name: workflowId })).toBeVisible({
    timeout: SEARCH_INDEX_TIMEOUT_MS,
  });
  await expect(page.getByText(PARENT_WF_NAME).first()).toBeVisible();
});

test("Run Workflow UI starts the multi-task workflow and it completes", async ({
  page,
}) => {
  test.setTimeout(EXECUTION_TIMEOUT_MS + 90_000);

  await page.goto("/runWorkflow");
  await page.waitForLoadState("networkidle");

  // MUI Autocomplete puts `id` on the root, not the <input> — use the
  // accessible combobox name from the field label.
  const nameField = page.getByRole("combobox", { name: "Workflow name" });
  await expect(nameField).toBeVisible({ timeout: 15_000 });
  await nameField.fill(PARENT_WF_NAME);
  await page.getByRole("option", { name: PARENT_WF_NAME }).click();

  const versionField = page.getByRole("combobox", { name: "Version" });
  await expect(versionField).toBeVisible();
  if ((await versionField.inputValue()) !== "1") {
    await versionField.click();
    await page.getByRole("option", { name: "1", exact: true }).click();
  }

  // Wait for the input-params template to populate from workflow.inputParameters,
  // then replace the Monaco model value directly (keyboard select-all is flaky
  // against Monaco and left a corrupted JSON merge in earlier runs).
  await expect(
    page.getByRole("textbox", { name: "Editor content" }).first(),
  ).toBeVisible({ timeout: 30_000 });
  await expect
    .poll(async () =>
      page.evaluate(() => {
        const monaco = (
          window as unknown as {
            monaco?: {
              editor: { getModels: () => Array<{ getValue: () => string }> };
            };
          }
        ).monaco;
        return (
          monaco?.editor
            .getModels()
            .some((m) => m.getValue().includes("orderId")) ?? false
        );
      }),
    )
    .toBe(true);

  const inputJson = JSON.stringify(ORDER_INPUT, null, 2);
  await page.evaluate((json) => {
    const monaco = (
      window as unknown as {
        monaco: {
          editor: {
            getModels: () => Array<{
              getValue: () => string;
              setValue: (v: string) => void;
            }>;
          };
        };
      }
    ).monaco;
    const models = monaco.editor.getModels();
    const target =
      models.find((m) => m.getValue().includes("orderId")) ?? models[0];
    if (!target) {
      throw new Error("No Monaco model found for Input params");
    }
    target.setValue(json);
  }, inputJson);

  // Confirm the model actually holds valid JSON before running.
  await expect
    .poll(async () =>
      page.evaluate(() => {
        const monaco = (
          window as unknown as {
            monaco?: {
              editor: { getModels: () => Array<{ getValue: () => string }> };
            };
          }
        ).monaco;
        const model = monaco?.editor
          .getModels()
          .find((m) => m.getValue().includes("ORD-42"));
        try {
          return JSON.parse(model?.getValue() ?? "");
        } catch {
          return null;
        }
      }),
    )
    .toMatchObject(ORDER_INPUT);

  await page.getByRole("button", { name: "Run workflow" }).click();

  const alert = page.locator("#workflow-created-alert");
  await expect(alert).toBeVisible({ timeout: 30_000 });

  const executionLink = page.locator("#workflow-execution-id");
  await expect(executionLink).toBeVisible();
  const workflowId = (await executionLink.innerText()).trim();
  expect(workflowId.length).toBeGreaterThan(0);
  startedWorkflowIds.push(workflowId);

  const wf = await waitForWorkflow(workflowId, {
    timeoutMs: EXECUTION_TIMEOUT_MS,
  });
  assertApiExecution(wf);

  await executionLink.click();
  await expect(page).toHaveURL(new RegExp(`/execution/${workflowId}`), {
    timeout: 15_000,
  });
  await page.waitForLoadState("networkidle");
  await expectExecutionStatusChip(page, "COMPLETED");
  await expect(page.getByText("enrich_order_ref").first()).toBeVisible({
    timeout: 15_000,
  });
});

// ════════════════════════════════════════════════════════════════════════════
// Extra task types — EVENT, HTTP_POLL, SIMPLE worker, START_WORKFLOW,
//                   GET_WORKFLOW
// Each workflow is minimal (1-2 tasks) so tests stay focused on the task
// type being exercised rather than repeating the full topology tour.
// ════════════════════════════════════════════════════════════════════════════

// ── Workflow definitions ──────────────────────────────────────────────────────

/**
 * EVENT → SET_VARIABLE
 *
 * EVENT tasks fire a message onto the specified conductor queue and complete
 * immediately without a worker.  The SET_VARIABLE captures a flag so we have
 * a workflow variable to assert on.
 */
const EVENT_WORKFLOW: WorkflowDef = {
  name: EVENT_WF_NAME,
  version: 1,
  description: "EVENT task e2e — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  inputParameters: ["payload"],
  outputParameters: {
    eventFired: "${workflow.variables.eventFired}",
  },
  tasks: [
    {
      name: "fire_event",
      taskReferenceName: "fire_event_ref",
      type: "EVENT",
      sink: EVENT_SINK,
      inputParameters: { payload: "${workflow.input.payload}" },
    },
    {
      name: "mark_event_sent",
      taskReferenceName: "mark_event_ref",
      type: "SET_VARIABLE",
      inputParameters: { eventFired: true },
    },
  ],
};

/**
 * SIMPLE worker → SET_VARIABLE
 *
 * SIMPLE tasks park in SCHEDULED/IN_PROGRESS until a worker calls
 * POST /api/tasks.  The test acts as the worker: it polls the execution until
 * the task is SCHEDULED, then calls updateTask() to complete it and waits for
 * the workflow to finish.
 */
const SIMPLE_WORKFLOW: WorkflowDef = {
  name: SIMPLE_WF_NAME,
  version: 1,
  description: "SIMPLE worker task e2e — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  inputParameters: ["jobId"],
  outputParameters: {
    workerResult: "${worker_task_ref.output.workerResult}",
    done: "${workflow.variables.done}",
  },
  tasks: [
    {
      name: SIMPLE_TASK_TYPE,
      taskReferenceName: "worker_task_ref",
      type: "SIMPLE",
      inputParameters: { jobId: "${workflow.input.jobId}" },
    },
    {
      name: "mark_done",
      taskReferenceName: "mark_done_ref",
      type: "SET_VARIABLE",
      inputParameters: { done: true },
    },
  ],
};

/**
 * START_WORKFLOW → GET_WORKFLOW → SET_VARIABLE
 *
 * START_WORKFLOW launches an existing workflow and surfaces its ID in output.
 * GET_WORKFLOW is not exercised here because that system task executor is not
 * available in the OSS Docker image used for integration tests.
 */
const LAUNCH_WORKFLOW: WorkflowDef = {
  name: LAUNCH_WF_NAME,
  version: 1,
  description: "START_WORKFLOW e2e — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  outputParameters: {
    launchedId: "${launch_child_ref.output.workflowId}",
  },
  tasks: [
    {
      name: "launch_child",
      taskReferenceName: "launch_child_ref",
      type: "START_WORKFLOW",
      inputParameters: {
        startWorkflow: {
          name: CHILD_WF_NAME,
          version: 1,
          input: { parentOrderId: "e2e-launch-test" },
        },
      },
    },
    {
      name: "store_launch_result",
      taskReferenceName: "store_launch_ref",
      type: "SET_VARIABLE",
      inputParameters: { launchedId: "${launch_child_ref.output.workflowId}" },
    },
  ],
};

// ── Helper — wait until a specific task reaches SCHEDULED or IN_PROGRESS ─────

async function waitForTaskScheduled(
  workflowId: string,
  taskRef: string,
  timeoutMs = 15_000,
): Promise<WorkflowTaskExecution> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const wf = await getWorkflowExecution(workflowId);
    const task = wf.tasks?.find((t) => t.referenceTaskName === taskRef);
    if (
      task?.taskId &&
      (task.status === "SCHEDULED" || task.status === "IN_PROGRESS")
    ) {
      return task;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(
    `Task ${taskRef} in workflow ${workflowId} did not reach SCHEDULED/IN_PROGRESS within ${timeoutMs}ms`,
  );
}

// ── Test suite ────────────────────────────────────────────────────────────────

test.describe("Extra task types", () => {
  const extraWorkflowIds: string[] = [];

  test.beforeAll(async () => {
    // Register the SIMPLE worker's task definition before the workflow that uses it.
    await createTaskDef({
      name: SIMPLE_TASK_TYPE,
      description: "SIMPLE worker for Playwright E2E — safe to delete",
      retryCount: 0,
      inputKeys: ["jobId"],
      outputKeys: ["workerResult"],
    });

    // CHILD_WF_NAME (SET_VARIABLE-only) is already registered by the outer
    // beforeAll.  Register only the new workflow definitions here.
    await createWorkflowDef(EVENT_WORKFLOW);
    await createWorkflowDef(SIMPLE_WORKFLOW);
    await createWorkflowDef(LAUNCH_WORKFLOW);
  });

  test.afterAll(async () => {
    await Promise.allSettled(
      extraWorkflowIds.map((id) => terminateWorkflow(id)),
    );
    await deleteWorkflowDef(EVENT_WF_NAME).catch(() => {});
    await deleteWorkflowDef(SIMPLE_WF_NAME).catch(() => {});
    await deleteWorkflowDef(LAUNCH_WF_NAME).catch(() => {});
    await deleteTaskDef(SIMPLE_TASK_TYPE).catch(() => {});
  });

  // ── EVENT task ───────────────────────────────────────────────────────────────

  test("EVENT task workflow completes and fires the event (API)", async () => {
    test.setTimeout(EXECUTION_TIMEOUT_MS);

    const workflowId = await startWorkflow(EVENT_WF_NAME, {
      payload: "e2e-test-event-payload",
    });
    extraWorkflowIds.push(workflowId);

    const wf = await waitForWorkflow(workflowId, {
      timeoutMs: EXECUTION_TIMEOUT_MS,
    });

    expect(wf.status).toBe("COMPLETED");

    const eventTask = wf.tasks?.find(
      (t) => t.referenceTaskName === "fire_event_ref",
    );
    expect(eventTask?.taskType).toBe("EVENT");
    expect(eventTask?.status).toBe("COMPLETED");

    const markTask = wf.tasks?.find(
      (t) => t.referenceTaskName === "mark_event_ref",
    );
    expect(markTask?.taskType).toBe("SET_VARIABLE");
    expect(markTask?.status).toBe("COMPLETED");

    expect(wf.variables?.eventFired).toBe(true);
    expect(wf.output?.eventFired).toBe(true);
  });

  test("EVENT task shows correct type and COMPLETED status in UI", async ({
    page,
  }) => {
    test.setTimeout(EXECUTION_TIMEOUT_MS + 30_000);

    const workflowId = await startWorkflow(EVENT_WF_NAME, {
      payload: "e2e-ui-test",
    });
    extraWorkflowIds.push(workflowId);

    const wf = await waitForWorkflow(workflowId, {
      timeoutMs: EXECUTION_TIMEOUT_MS,
    });
    expect(wf.status).toBe("COMPLETED");

    await page.goto(`/execution/${workflowId}`);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText(EVENT_WF_NAME).first()).toBeVisible({
      timeout: 15_000,
    });
    await expectExecutionStatusChip(page, "COMPLETED");

    // Diagram shows both task reference labels.
    await expect(page.getByText("fire_event_ref").first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText("mark_event_ref").first()).toBeVisible();

    // Click the EVENT task card to open the right panel.
    await page.getByText("fire_event_ref").first().click();
    const rightPanel = page.locator("#execution-page-right-panel");
    await expect(rightPanel).toBeVisible({ timeout: 15_000 });

    // Summary tab — TaskSummary KeyValueTable rows
    await expect(rightPanel.getByText("Task type").first()).toBeVisible();
    await expect(rightPanel.getByText("EVENT").first()).toBeVisible();
    await expect(rightPanel.getByText("Task reference").first()).toBeVisible();
    await expect(rightPanel.getByText("fire_event_ref").first()).toBeVisible();
    await expect(
      rightPanel
        .locator(".MuiChip-label")
        .filter({ hasText: /^Completed$/ })
        .first(),
    ).toBeVisible();

    // Input tab — payload and sink parameters
    await rightPanel.getByRole("tab", { name: "Input" }).click();
    await expect(
      rightPanel.getByText(/e2e-ui-test|payload/i).first(),
    ).toBeVisible({ timeout: 15_000 });
  });

  // ── SIMPLE worker task ────────────────────────────────────────────────────────

  test("SIMPLE worker task: test acts as worker via API to complete it", async () => {
    test.setTimeout(EXECUTION_TIMEOUT_MS);

    const workflowId = await startWorkflow(SIMPLE_WF_NAME, {
      jobId: "e2e-job-42",
    });
    extraWorkflowIds.push(workflowId);

    // Wait until the workflow schedules the SIMPLE task.
    const task = await waitForTaskScheduled(workflowId, "worker_task_ref");
    expect(task.taskId).toBeTruthy();

    // The test acts as the worker: complete the task with a known output.
    await updateTask({
      taskId: task.taskId!,
      workflowInstanceId: workflowId,
      status: "COMPLETED",
      outputData: { workerResult: "processed-e2e-job-42" },
    });

    const wf = await waitForWorkflow(workflowId, {
      timeoutMs: EXECUTION_TIMEOUT_MS,
    });

    expect(wf.status).toBe("COMPLETED");

    const workerTask = wf.tasks?.find(
      (t) => t.referenceTaskName === "worker_task_ref",
    );
    // taskType on an executed task is the registered task-def name, not the
    // schema keyword "SIMPLE".
    expect(workerTask?.taskType).toBe(SIMPLE_TASK_TYPE);
    expect(workerTask?.status).toBe("COMPLETED");
    expect(workerTask?.outputData?.workerResult).toBe("processed-e2e-job-42");

    const markTask = wf.tasks?.find(
      (t) => t.referenceTaskName === "mark_done_ref",
    );
    expect(markTask?.status).toBe("COMPLETED");

    expect(wf.variables?.done).toBe(true);
    expect(wf.output?.workerResult).toBe("processed-e2e-job-42");
  });

  test("SIMPLE task shows SCHEDULED state in UI while waiting for worker", async ({
    page,
  }) => {
    test.setTimeout(EXECUTION_TIMEOUT_MS + 30_000);

    const workflowId = await startWorkflow(SIMPLE_WF_NAME, {
      jobId: "e2e-ui-job",
    });
    extraWorkflowIds.push(workflowId);

    // Navigate to the execution before completing the task so we can assert
    // the SCHEDULED / IN_PROGRESS state in the UI.
    const task = await waitForTaskScheduled(workflowId, "worker_task_ref");

    await page.goto(`/execution/${workflowId}`);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText(SIMPLE_WF_NAME).first()).toBeVisible({
      timeout: 15_000,
    });

    // The workflow is RUNNING while the worker task waits.
    await expect(
      page
        .locator(".MuiChip-label")
        .filter({ hasText: /^Running$/ })
        .first(),
    ).toBeVisible({ timeout: 15_000 });

    // Click the SIMPLE task node to open the right panel.
    await page.getByText("worker_task_ref").first().click();
    const rightPanel = page.locator("#execution-page-right-panel");
    await expect(rightPanel).toBeVisible({ timeout: 15_000 });

    // Summary: task type is SIMPLE, status is SCHEDULED or IN_PROGRESS.
    await expect(rightPanel.getByText("Task type").first()).toBeVisible();
    await expect(rightPanel.getByText("SIMPLE").first()).toBeVisible();
    await expect(rightPanel.getByText("Task reference").first()).toBeVisible();
    await expect(rightPanel.getByText("worker_task_ref").first()).toBeVisible();
    // Status chip shows Scheduled or In Progress.
    await expect(
      rightPanel
        .locator(".MuiChip-label")
        .filter({ hasText: /^(Scheduled|In progress)$/ })
        .first(),
    ).toBeVisible({ timeout: 15_000 });

    // Input tab: the jobId parameter is present.
    await rightPanel.getByRole("tab", { name: "Input" }).click();
    await expect(rightPanel.getByText(/e2e-ui-job|jobId/i).first()).toBeVisible(
      { timeout: 15_000 },
    );

    // Now complete the task so the workflow finishes (clean up state).
    await updateTask({
      taskId: task.taskId!,
      workflowInstanceId: workflowId,
      status: "COMPLETED",
      outputData: { workerResult: "done-from-ui-test" },
    });
    await waitForWorkflow(workflowId, { timeoutMs: EXECUTION_TIMEOUT_MS });
  });

  // ── START_WORKFLOW task ───────────────────────────────────────────────────────

  test("START_WORKFLOW workflow launches child and completes (API)", async () => {
    test.setTimeout(EXECUTION_TIMEOUT_MS + 30_000);

    const workflowId = await startWorkflow(LAUNCH_WF_NAME, {});
    extraWorkflowIds.push(workflowId);

    const wf = await waitForWorkflow(workflowId, {
      timeoutMs: EXECUTION_TIMEOUT_MS + 15_000,
    });

    expect(wf.status).toBe("COMPLETED");

    // START_WORKFLOW task
    const launchTask = wf.tasks?.find(
      (t) => t.referenceTaskName === "launch_child_ref",
    );
    expect(launchTask?.taskType).toBe("START_WORKFLOW");
    expect(launchTask?.status).toBe("COMPLETED");
    // The launched child workflow ID is surfaced in outputData.workflowId.
    const launchedId = launchTask?.outputData?.workflowId as string | undefined;
    expect(launchedId).toBeTruthy();

    // SET_VARIABLE stores the child ID in workflow variables.
    const storeTask = wf.tasks?.find(
      (t) => t.referenceTaskName === "store_launch_ref",
    );
    expect(storeTask?.status).toBe("COMPLETED");

    // Workflow-level output contains the launched child ID.
    expect(wf.output?.launchedId).toBeTruthy();
  });

  test("START_WORKFLOW task shows launched child link in right panel", async ({
    page,
  }) => {
    test.setTimeout(EXECUTION_TIMEOUT_MS + 60_000);

    const workflowId = await startWorkflow(LAUNCH_WF_NAME, {});
    extraWorkflowIds.push(workflowId);

    const wf = await waitForWorkflow(workflowId, {
      timeoutMs: EXECUTION_TIMEOUT_MS + 15_000,
    });
    expect(wf.status).toBe("COMPLETED");

    const launchedId = wf.tasks?.find(
      (t) => t.referenceTaskName === "launch_child_ref",
    )?.outputData?.workflowId as string | undefined;
    expect(launchedId).toBeTruthy();

    await page.goto(`/execution/${workflowId}`);
    await page.waitForLoadState("networkidle");
    await expect(page.getByText(LAUNCH_WF_NAME).first()).toBeVisible({
      timeout: 15_000,
    });
    await expectExecutionStatusChip(page, "COMPLETED");

    // Diagram shows both task reference labels.
    for (const ref of ["launch_child_ref", "store_launch_ref"]) {
      await expectTaskRefVisible(page, ref);
    }

    // Open START_WORKFLOW task right panel.
    await page.getByText("launch_child_ref").first().click();
    const rightPanel = page.locator("#execution-page-right-panel");
    await expect(rightPanel).toBeVisible({ timeout: 15_000 });

    await expect(rightPanel.getByText("Task type").first()).toBeVisible();
    await expect(rightPanel.getByText("START_WORKFLOW").first()).toBeVisible();
    await expect(rightPanel.getByText("Task reference").first()).toBeVisible();
    await expect(
      rightPanel.getByText("launch_child_ref").first(),
    ).toBeVisible();
    await expect(
      rightPanel
        .locator(".MuiChip-label")
        .filter({ hasText: /^Completed$/ })
        .first(),
    ).toBeVisible();

    // Output tab: the launched workflow ID link is rendered by TaskSummary.
    await rightPanel.getByRole("tab", { name: "Output" }).click();
    await expect(
      rightPanel.getByText(/workflowId|Start workflow/i).first(),
    ).toBeVisible({ timeout: 15_000 });
  });
});
