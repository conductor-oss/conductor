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
  createWorkflowDef,
  deleteWorkflowDef,
  getWorkflowDef,
  startWorkflow,
  terminateWorkflow,
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
