/**
 * Integration tests — Event handlers actually firing.
 *
 * event-handler-actions.spec.ts checks that the form saves the right JSON.
 * These go one step further: build a handler through the Event tab, publish a
 * real event, and assert the action's effect on the server.
 *
 * How an event is produced: an "emitter" workflow runs one EVENT task with
 * sink `conductor`. The server publishes the task's output (its input plus
 * workflowInstanceId, workflowType, …) to the queue
 * `conductor:<emitterWorkflow>:<taskRef>` (Event.computeQueueName), and that
 * message is the payload handler expressions resolve against — so `${orderId}`
 * reads the emitter's `orderId` input.
 *
 * Timing: the server only starts listening on a new handler's queue in
 * DefaultEventQueueManager.refreshEventQueues, every 60s. The conductor queue
 * keeps the message until then, so each test waits up to ~2 minutes for the
 * effect. Tests run in parallel to share those refresh windows.
 *
 * OSS SimpleActionProcessor only runs complete_task, fail_task,
 * start_workflow and start_agent; terminate_workflow and
 * update_workflow_variables save but fail when the event fires. The last
 * describe block pins that.
 */

import type { Page } from "@playwright/test";
import { expect, test } from "../coverage-fixture";
import {
  createEventHandler,
  createWorkflowDef,
  deleteAgent,
  deleteEventHandler,
  deleteWorkflowDef,
  deployAgent,
  getWorkflowExecution,
  isAgentApiAvailable,
  searchWorkflows,
  startWorkflow,
  terminateWorkflow,
  waitForWorkflow,
  type WorkflowDef,
  type WorkflowExecution,
} from "./api-client";
import {
  actionCard,
  addAction,
  addKeyValue,
  replaceTemplateAction,
  saveHandler,
  section,
  setCondition,
  startNewHandler,
} from "./event-handler-form";

test.describe.configure({ mode: "parallel" });

// Parallel mode loads this file once per worker, and each worker runs its own
// beforeAll/afterAll. Date.now() alone can match across workers that start in
// the same millisecond, so they'd share fixture names and one worker's
// afterAll would delete the other's workflow defs mid-test.
const RUN_ID = `${Date.now()}_w${process.env.TEST_WORKER_INDEX ?? 0}`;
const BASE =
  (process.env.CONDUCTOR_SERVER_URL ?? "http://localhost:8000") + "/api";

/** Long enough for one 60s queue refresh plus polling slack. */
const EFFECT_TIMEOUT = 150_000;
/** After a control effect lands, how long to watch for one that must not. */
const SETTLE_MS = 8_000;

const OWNER = "e2e@conductor.test";
const WAIT_REF = "wait_ref";
const EMIT_REF = "emit_ref";
/** Emitter inputs copied into the event payload. */
const PAYLOAD_KEYS = [
  "targetWorkflowId",
  "otherWorkflowId",
  "taskId",
  "amount",
  "orderId",
  "question",
];

// ── Fixtures ──────────────────────────────────────────────────────────────────

/** Parks on a WAIT task until a handler completes or fails it. */
const TARGET_WF = `e2e_eh_rt_target_${RUN_ID}`;
/** Started by start_workflow actions; finishes on its own. */
const STARTED_WF = `e2e_eh_rt_started_${RUN_ID}`;

const targetDef: WorkflowDef = {
  name: TARGET_WF,
  version: 1,
  ownerEmail: OWNER,
  schemaVersion: 2,
  tasks: [{ name: "wait", taskReferenceName: WAIT_REF, type: "WAIT" }],
};

const startedDef = (version: number): WorkflowDef => ({
  name: STARTED_WF,
  version,
  ownerEmail: OWNER,
  schemaVersion: 2,
  inputParameters: ["orderId"],
  tasks: [
    {
      name: "mark",
      taskReferenceName: "mark_ref",
      type: "SET_VARIABLE",
      inputParameters: { version },
    },
  ],
});

const emitters: string[] = [];
const handlers: string[] = [];
const workflows: string[] = [];

/** Creates an emitter workflow and returns the event name it publishes to. */
async function createEmitter(slug: string) {
  const name = `e2e_eh_rt_emit_${slug}_${RUN_ID}`;
  emitters.push(name);
  await createWorkflowDef({
    name,
    version: 1,
    ownerEmail: OWNER,
    schemaVersion: 2,
    inputParameters: PAYLOAD_KEYS,
    tasks: [
      {
        name: "emit",
        taskReferenceName: EMIT_REF,
        type: "EVENT",
        sink: "conductor",
        inputParameters: Object.fromEntries(
          PAYLOAD_KEYS.map((key) => [key, `\${workflow.input.${key}}`]),
        ),
      },
    ],
  });
  return { name, event: `conductor:${name}:${EMIT_REF}` };
}

/** Runs the emitter, publishing one event with `payload`. */
async function fire(emitter: string, payload: Record<string, unknown>) {
  const id = await startWorkflow(emitter, payload);
  workflows.push(id);
  const done = await waitForWorkflow(id);
  expect(done.status, "emitter should publish and complete").toBe("COMPLETED");
}

/** Starts a target workflow and waits until its WAIT task is in progress. */
async function startTarget() {
  const id = await startWorkflow(TARGET_WF);
  workflows.push(id);
  await expect
    .poll(async () => waitTask(await getWorkflowExecution(id))?.status)
    .toBe("IN_PROGRESS");
  return id;
}

const waitTask = (execution: WorkflowExecution) =>
  execution.tasks?.find((t) => t.referenceTaskName === WAIT_REF);

async function waitTaskOf(workflowId: string) {
  return waitTask(await getWorkflowExecution(workflowId))!;
}

/** Polls until the target's WAIT task leaves IN_PROGRESS. */
async function expectWaitTask(workflowId: string, status: string) {
  await expect
    .poll(async () => (await waitTaskOf(workflowId)).status, {
      timeout: EFFECT_TIMEOUT,
      intervals: [2_000],
    })
    .toBe(status);
  return waitTaskOf(workflowId);
}

async function correlated(name: string, correlationId: string) {
  const res = await fetch(
    `${BASE}/workflow/${name}/correlated/${encodeURIComponent(correlationId)}?includeClosed=true`,
  );
  expect(res.ok).toBe(true);
  return (await res.json()) as Array<
    WorkflowExecution & { workflowVersion: number; correlationId: string }
  >;
}

/** Waits until exactly one workflow `name` with `correlationId` exists. */
async function expectStarted(name: string, correlationId: string) {
  await expect
    .poll(async () => (await correlated(name, correlationId)).length, {
      timeout: EFFECT_TIMEOUT,
      intervals: [2_000],
    })
    .toBe(1);
  const [started] = await correlated(name, correlationId);
  workflows.push(started.workflowId);
  return started;
}

/** Fills the task identification fields of a complete/fail card. */
async function identifyTask(
  page: Page,
  index: number,
  by: "workflow" | "taskId",
) {
  const card = actionCard(page, index);
  if (by === "taskId") {
    await card.locator("#task-id-radio-button").check();
    await card.getByLabel("Task ID", { exact: true }).fill("${taskId}");
  } else {
    await card
      .getByLabel("Workflow ID", { exact: true })
      .fill("${targetWorkflowId}");
    await card.getByLabel("Task reference name").fill(WAIT_REF);
  }
}

const handlerName = (slug: string) => {
  const name = `e2e_eh_rt_${slug}_${RUN_ID}`;
  handlers.push(name);
  return name;
};

test.beforeAll(async () => {
  await createWorkflowDef(targetDef);
  await createWorkflowDef(startedDef(1));
  await createWorkflowDef(startedDef(2));
});

// Delete each test's handlers as soon as it ends, not in afterAll. The server
// polls every active `conductor` queue on one shared pool (one thread per CPU,
// conductor.app.event-queue-scheduler-poll-thread-count), and an idle queue's
// long poll holds a thread for ~1s per tick. Once roughly that many handlers
// are active, queues started later are never polled and their events sit on
// the queue until the test times out.
test.afterEach(async () => {
  const names = handlers.splice(0);
  await Promise.all(
    names.map((name) => deleteEventHandler(name).catch(() => {})),
  );
});

test.afterAll(async () => {
  await Promise.all(
    workflows.map((id) => terminateWorkflow(id).catch(() => {})),
  );
  await Promise.all(
    [
      ...emitters.map((name) => deleteWorkflowDef(name)),
      deleteWorkflowDef(TARGET_WF),
      deleteWorkflowDef(STARTED_WF, 1),
      deleteWorkflowDef(STARTED_WF, 2),
    ].map((p) => p.catch(() => {})),
  );
});

test.beforeEach(() => {
  test.setTimeout(EFFECT_TIMEOUT + 90_000);
});

// ── complete_task / fail_task ─────────────────────────────────────────────────

for (const action of ["Complete Task", "Fail Task"] as const) {
  const status = action === "Complete Task" ? "COMPLETED" : "FAILED";

  for (const by of ["workflow", "taskId"] as const) {
    const label = by === "workflow" ? "workflow ID + task ref name" : "task ID";

    test(`${action} by ${label} sets the waiting task ${status}`, async ({
      page,
    }) => {
      const slug = `${status.toLowerCase()}_${by}`;
      const emitter = await createEmitter(slug);
      const name = handlerName(slug);

      await startNewHandler(page, name, emitter.event);
      if (action === "Fail Task") await replaceTemplateAction(page, action);
      await identifyTask(page, 0, by);
      await addKeyValue(actionCard(page), "Output", "orderId", "${orderId}");
      await saveHandler(page, name);

      const target = await startTarget();
      const taskId = (await waitTaskOf(target)).taskId;
      await fire(emitter.name, {
        targetWorkflowId: target,
        taskId,
        orderId: `order-${slug}`,
      });

      const task = await expectWaitTask(target, status);
      // Output values are expressions resolved against the event payload.
      expect(task.outputData).toMatchObject({ orderId: `order-${slug}` });
      const workflow = await waitForWorkflow(target);
      expect(workflow.status).toBe(status);
    });
  }
}

// ── start_workflow ────────────────────────────────────────────────────────────

test.describe("Start Workflow", () => {
  for (const pinned of [undefined, 1] as const) {
    const label = pinned ? `pinned to version ${pinned}` : "on Latest";

    test(`starts the workflow ${label} with payload input and correlation ID`, async ({
      page,
    }) => {
      const slug = pinned ? `start_v${pinned}` : "start_latest";
      const emitter = await createEmitter(slug);
      const name = handlerName(slug);

      await startNewHandler(page, name, emitter.event);
      await replaceTemplateAction(page, "Start Workflow");
      const card = actionCard(page);
      const wfName = card.getByLabel("Workflow name");
      await wfName.fill(STARTED_WF);
      await wfName.blur();
      if (pinned) {
        const version = card.getByLabel("Workflow version");
        await version.fill(String(pinned));
        await version.blur();
      }
      await card.getByLabel("Workflow correlation id").fill("${orderId}");
      await addKeyValue(card, "Input variables", "orderId", "${orderId}");
      await saveHandler(page, name);

      const orderId = `order-${slug}-${RUN_ID}`;
      await fire(emitter.name, { orderId });

      const started = await expectStarted(STARTED_WF, orderId);
      // Latest resolves when the event fires: version 2 is the newest.
      expect(started.workflowVersion).toBe(pinned ?? 2);
      expect(started.input).toMatchObject({
        orderId,
        "conductor.event.name": emitter.event,
      });
    });
  }
});

// ── start_agent ───────────────────────────────────────────────────────────────

test.describe("Start Agent", () => {
  const AGENT = `e2e_eh_rt_agent_${RUN_ID}`;

  test.beforeAll(async () => {
    test.skip(!(await isAgentApiAvailable()), "agent API not enabled");
    await deployAgent(AGENT);
  });

  test.afterAll(async () => {
    await deleteAgent(AGENT).catch(() => {});
  });

  test("starts an agent with a prompt and session ID from the payload", async ({
    page,
  }) => {
    const emitter = await createEmitter("agent");
    const name = handlerName("agent");

    await startNewHandler(page, name, emitter.event);
    await replaceTemplateAction(page, "Start Agent");
    const card = actionCard(page);
    const agentName = card.getByLabel("Agent name");
    await agentName.fill(AGENT);
    await agentName.blur();
    await card.getByLabel("Prompt").fill("Answer briefly: ${question}");
    await card.getByLabel("Session ID").fill("${orderId}");
    await saveHandler(page, name);

    const orderId = `session-${RUN_ID}`;
    await fire(emitter.name, { orderId, question: "what is 2 + 2?" });

    // An agent execution is a workflow named after the agent. The session ID
    // lands in its input as `session_id` (WorkflowExecutorOps
    // .startAgentExecution) — the correlation ID comes from the idempotency
    // key, which this form doesn't set — so find the run by name.
    await expect
      .poll(
        async () =>
          (await searchWorkflows({ query: `workflowType IN (${AGENT})` }))
            .totalHits,
        { timeout: EFFECT_TIMEOUT, intervals: [2_000] },
      )
      .toBe(1);
    const [hit] = (
      await searchWorkflows({ query: `workflowType IN (${AGENT})` })
    ).results;
    workflows.push(hit.workflowId);
    const started = await getWorkflowExecution(hit.workflowId);
    expect(started.input).toMatchObject({
      session_id: orderId,
      prompt: "Answer briefly: what is 2 + 2?",
    });
  });
});

// ── Conditions, Active, several actions ───────────────────────────────────────

test.describe("handler options", () => {
  test("only handlers whose condition matches the payload run", async ({
    page,
  }) => {
    const emitter = await createEmitter("condition");
    const matching = handlerName("cond_match");
    const notMatching = handlerName("cond_no_match");

    for (const [name, condition, target] of [
      [matching, "$.amount > 100", "${targetWorkflowId}"],
      [notMatching, "$.amount < 100", "${otherWorkflowId}"],
    ]) {
      await startNewHandler(page, name, emitter.event);
      await setCondition(page, condition);
      await actionCard(page)
        .getByLabel("Workflow ID", { exact: true })
        .fill(target);
      await actionCard(page).getByLabel("Task reference name").fill(WAIT_REF);
      await saveHandler(page, name);
    }

    const target = await startTarget();
    const other = await startTarget();
    await fire(emitter.name, {
      targetWorkflowId: target,
      otherWorkflowId: other,
      amount: 150,
    });

    await expectWaitTask(target, "COMPLETED");
    await new Promise((r) => setTimeout(r, SETTLE_MS));
    expect((await waitTaskOf(other)).status).toBe("IN_PROGRESS");
  });

  test("an inactive handler does not run", async ({ page }) => {
    const emitter = await createEmitter("inactive");
    const inactive = handlerName("inactive");
    const control = handlerName("inactive_control");

    await startNewHandler(page, inactive, emitter.event);
    await section(page, "details").getByLabel("Active").uncheck();
    await identifyTask(page, 0, "workflow");
    await saveHandler(page, inactive);

    // An active handler on the same event shows when the event was processed.
    await createEventHandler({
      name: control,
      event: emitter.event,
      condition: "true",
      evaluatorType: "javascript",
      active: true,
      actions: [
        {
          action: "complete_task",
          expandInlineJSON: false,
          complete_task: {
            workflowId: "${otherWorkflowId}",
            taskRefName: WAIT_REF,
          },
        },
      ],
    });

    const target = await startTarget();
    const other = await startTarget();
    await fire(emitter.name, {
      targetWorkflowId: target,
      otherWorkflowId: other,
    });

    await expectWaitTask(other, "COMPLETED");
    await new Promise((r) => setTimeout(r, SETTLE_MS));
    expect((await waitTaskOf(target)).status).toBe("IN_PROGRESS");
  });

  test("every action of a handler runs", async ({ page }) => {
    const emitter = await createEmitter("multi");
    const name = handlerName("multi");

    await startNewHandler(page, name, emitter.event);
    await addAction(page, "Fail Task");
    // Index 0: the new Fail Task; index 1: the template's Complete Task.
    await actionCard(page, 0)
      .getByLabel("Workflow ID", { exact: true })
      .fill("${otherWorkflowId}");
    await actionCard(page, 0).getByLabel("Task reference name").fill(WAIT_REF);
    await identifyTask(page, 1, "workflow");
    await saveHandler(page, name);

    const target = await startTarget();
    const other = await startTarget();
    await fire(emitter.name, {
      targetWorkflowId: target,
      otherWorkflowId: other,
    });

    await expectWaitTask(target, "COMPLETED");
    await expectWaitTask(other, "FAILED");
  });
});

// ── Actions OSS saves but does not run ────────────────────────────────────────

/**
 * SimpleActionProcessor throws "Action not supported" for these, so the event
 * execution fails and the workflow is untouched. A Complete Task on another
 * workflow in the same handler marks when the event was processed. If OSS
 * starts supporting either action, these fail and should be flipped.
 */
test.describe("actions OSS does not run", () => {
  test("Terminate Workflow leaves the workflow running", async ({ page }) => {
    const emitter = await createEmitter("terminate");
    const name = handlerName("terminate");

    await startNewHandler(page, name, emitter.event);
    await addAction(page, "Terminate Workflow");
    await actionCard(page, 0)
      .getByLabel("Workflow ID", { exact: true })
      .fill("${targetWorkflowId}");
    await actionCard(page, 0)
      .getByLabel("Termination reason")
      .fill("from event");
    await actionCard(page, 1)
      .getByLabel("Workflow ID", { exact: true })
      .fill("${otherWorkflowId}");
    await actionCard(page, 1).getByLabel("Task reference name").fill(WAIT_REF);
    await saveHandler(page, name);

    const target = await startTarget();
    const other = await startTarget();
    await fire(emitter.name, {
      targetWorkflowId: target,
      otherWorkflowId: other,
    });

    await expectWaitTask(other, "COMPLETED");
    await new Promise((r) => setTimeout(r, SETTLE_MS));
    expect((await getWorkflowExecution(target)).status).toBe("RUNNING");
  });

  test("Update Variables leaves the variables unchanged", async ({ page }) => {
    const emitter = await createEmitter("update_vars");
    const name = handlerName("update_vars");

    await startNewHandler(page, name, emitter.event);
    await addAction(page, "Update Variables");
    const card = actionCard(page, 0);
    await card
      .getByLabel("Workflow ID", { exact: true })
      .fill("${targetWorkflowId}");
    await addKeyValue(card, "Variables", "orderId", "${orderId}");
    await actionCard(page, 1)
      .getByLabel("Workflow ID", { exact: true })
      .fill("${otherWorkflowId}");
    await actionCard(page, 1).getByLabel("Task reference name").fill(WAIT_REF);
    await saveHandler(page, name);

    const target = await startTarget();
    const other = await startTarget();
    await fire(emitter.name, {
      targetWorkflowId: target,
      otherWorkflowId: other,
      orderId: "should-not-land",
    });

    await expectWaitTask(other, "COMPLETED");
    await new Promise((r) => setTimeout(r, SETTLE_MS));
    const execution = await getWorkflowExecution(target);
    expect(execution.status).toBe("RUNNING");
    expect(execution.variables ?? {}).not.toHaveProperty("orderId");
  });
});
