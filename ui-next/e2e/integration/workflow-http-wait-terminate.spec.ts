/**
 * Integration tests — HTTP, WAIT, INLINE (JavaScript), and TERMINATE
 *
 * Topology (all built-in system tasks, no workers):
 *
 *   js_compute     INLINE (evaluatorType: javascript)
 *     → http_ping  HTTP  GET https://orkes-api-tester.orkesconductor.com/api
 *     → short_wait WAIT  1 second
 *     → finish     TERMINATE  status=COMPLETED (+ workflowOutput)
 *
 * TERMINATE with terminationStatus=COMPLETED ends the workflow successfully
 * (acts like an early return). WAIT may take longer than 1s wall-clock time
 * because the server wait-sweeper polls on an interval.
 */

import { expect, test } from "../coverage-fixture";
import {
  createWorkflowDef,
  deleteWorkflowDef,
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
const WF_NAME = `e2e_http_wait_js_term_${RUN_ID}`;
/** WAIT sweeper can lag ~15s after the configured duration — keep headroom. */
const EXECUTION_TIMEOUT_MS = 90_000;
const HTTP_URI = "https://orkes-api-tester.orkesconductor.com/api";

const WORKFLOW: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description:
    "HTTP + WAIT + INLINE(JS) + TERMINATE e2e workflow — safe to delete",
  ownerEmail: "e2e@conductor.test",
  schemaVersion: 2,
  inputParameters: ["message"],
  outputParameters: {
    // Populated by TERMINATE.workflowOutput
    greeting: "${finish_ref.output.greeting}",
    httpStatus: "${finish_ref.output.httpStatus}",
    jsResult: "${finish_ref.output.jsResult}",
  },
  tasks: [
    {
      name: "js_compute",
      taskReferenceName: "js_compute_ref",
      type: "INLINE",
      inputParameters: {
        evaluatorType: "javascript",
        expression: "(function () { return 'hello-' + $.message; })();",
        message: "${workflow.input.message}",
      },
    },
    {
      name: "http_ping",
      taskReferenceName: "http_ping_ref",
      type: "HTTP",
      inputParameters: {
        http_request: {
          uri: HTTP_URI,
          method: "GET",
          connectionTimeOut: 5_000,
          readTimeOut: 5_000,
          accept: "application/json",
          contentType: "application/json",
        },
      },
    },
    {
      name: "short_wait",
      taskReferenceName: "short_wait_ref",
      type: "WAIT",
      inputParameters: {
        duration: "1 seconds",
      },
    },
    {
      name: "finish",
      taskReferenceName: "finish_ref",
      type: "TERMINATE",
      inputParameters: {
        terminationStatus: "COMPLETED",
        terminationReason: "e2e http/wait/js/terminate path finished",
        workflowOutput: {
          greeting: "${js_compute_ref.output.result}",
          httpStatus: "${http_ping_ref.output.response.statusCode}",
          jsResult: "${js_compute_ref.output.result}",
        },
      },
    },
  ],
};

const EXPECTED_COMPLETED_REFS = [
  "js_compute_ref",
  "http_ping_ref",
  "short_wait_ref",
  "finish_ref",
] as const;

const startedWorkflowIds: string[] = [];

test.beforeAll(async () => {
  await createWorkflowDef(WORKFLOW);
});

test.afterAll(async () => {
  await Promise.allSettled(
    startedWorkflowIds.map((id) => terminateWorkflow(id)),
  );
  await deleteWorkflowDef(WF_NAME).catch(() => {});
});

test.describe.configure({ mode: "serial" });

// ── Helpers ────────────────────────────────────────────────────────────────────

function taskByRef(
  wf: WorkflowExecution,
  ref: string,
): WorkflowTaskExecution | undefined {
  return wf.tasks?.find((t) => t.referenceTaskName === ref);
}

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

async function runToCompletion(input: Record<string, unknown> = {}) {
  const workflowId = await startWorkflow(WF_NAME, {
    message: "conductor",
    ...input,
  });
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
  expect(wf.workflowName ?? wf.workflowType).toBe(WF_NAME);
  expect(wf.status).toBe("COMPLETED");
  expect(wf.tasks?.length).toBeGreaterThan(0);

  for (const ref of EXPECTED_COMPLETED_REFS) {
    const task = taskByRef(wf, ref);
    expect(task, `missing task ${ref}`).toBeTruthy();
    expect(task!.status, `${ref} status`).toBe("COMPLETED");
  }

  // INLINE (javascript)
  const js = taskByRef(wf, "js_compute_ref");
  expect(js?.taskType).toBe("INLINE");
  expect(js?.outputData?.result).toBe("hello-conductor");

  // HTTP
  const http = taskByRef(wf, "http_ping_ref");
  expect(http?.taskType).toBe("HTTP");
  const response = http?.outputData?.response as
    | { statusCode?: number; body?: unknown }
    | undefined;
  expect(response?.statusCode).toBe(200);
  expect(response?.body).toBeTruthy();

  // WAIT
  const wait = taskByRef(wf, "short_wait_ref");
  expect(wait?.taskType).toBe("WAIT");

  // TERMINATE — task completes; workflow status is COMPLETED via terminationStatus
  const term = taskByRef(wf, "finish_ref");
  expect(term?.taskType).toBe("TERMINATE");
  expect(term?.outputData?.greeting).toBe("hello-conductor");
  expect(term?.outputData?.httpStatus).toBe(200);
  expect(term?.outputData?.jsResult).toBe("hello-conductor");

  // workflowOutput from TERMINATE becomes the workflow output
  expect(wf.output?.greeting).toBe("hello-conductor");
  expect(wf.output?.httpStatus).toBe(200);
  expect(wf.output?.jsResult).toBe("hello-conductor");
}

// ── Tests ─────────────────────────────────────────────────────────────────────

test("HTTP/WAIT/JS/TERMINATE workflow appears in the definitions list", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");
  await expect(page.getByText(WF_NAME)).toBeVisible({ timeout: 15_000 });
});

test("definition editor shows HTTP, WAIT, INLINE, and TERMINATE tasks", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_NAME}/1`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#workflow-name-display")).toBeVisible();

  for (const ref of EXPECTED_COMPLETED_REFS) {
    await expect(page.getByText(ref).first()).toBeVisible({ timeout: 15_000 });
  }

  await page.getByText("js_compute_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("INLINE", { exact: true }),
  ).toBeVisible();

  await page.getByText("http_ping_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("HTTP", { exact: true }),
  ).toBeVisible();

  await page.getByText("short_wait_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("WAIT", { exact: true }),
  ).toBeVisible();

  await page.getByText("finish_ref").first().click();
  await expect(
    page.locator("#maybe-task-form").getByText("TERMINATE", { exact: true }),
  ).toBeVisible();

  await setDiagramSnapshotViewport(page);
  await fitDiagramToScreen(page);
  // Clicking finish_ref scrolls the canvas — fit brings Start + js_compute back.
  await expect(page.getByText("js_compute_ref").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText("Start").first()).toBeVisible();

  await expectMainContentScreenshot(
    page,
    "http-wait-js-terminate-definition.png",
    {
      mask: [
        page.locator("#workflow-name-display"),
        page.locator("#maybe-task-form"),
      ],
    },
  );
});

test("executing HTTP + WAIT + JS + TERMINATE workflow completes successfully", async () => {
  test.setTimeout(EXECUTION_TIMEOUT_MS + 30_000);

  const { wf } = await runToCompletion();
  assertApiExecution(wf);
});

test("completed HTTP/WAIT/JS/TERMINATE execution shows status and task outputs", async ({
  page,
}) => {
  test.setTimeout(EXECUTION_TIMEOUT_MS + 60_000);

  const { workflowId, wf } = await runToCompletion();
  assertApiExecution(wf);

  await page.goto(`/execution/${workflowId}`);
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();
  await expect(page.getByText(WF_NAME).first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText(workflowId).first()).toBeVisible();
  await expectExecutionStatusChip(page, "COMPLETED");

  for (const ref of EXPECTED_COMPLETED_REFS) {
    await expect(page.getByText(ref).first()).toBeVisible({ timeout: 15_000 });
  }

  await expectTaskOutputVisible(page, "js_compute_ref", /hello-conductor/);
  // ReactJson may collapse nested statusCode — reasonPhrase "OK" is visible at the top.
  await expectTaskOutputVisible(
    page,
    "http_ping_ref",
    /OK|reasonPhrase|response/,
  );
  await expectTaskOutputVisible(page, "finish_ref", /hello-conductor/);

  await waitForExecutionDiagramReady(page);
  await page.keyboard.press("Escape");
  await setDiagramSnapshotViewport(page);
  await fitDiagramToScreen(page);
  await expect(page.getByText("js_compute_ref").first()).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.getByText("Start").first()).toBeVisible();
  await expectMainContentScreenshot(
    page,
    "http-wait-js-terminate-execution.png",
    {
      mask: [
        page.locator("#execution-page-right-panel"),
        page.getByRole("heading").filter({ hasText: /e2e_/ }),
      ],
    },
  );
});
