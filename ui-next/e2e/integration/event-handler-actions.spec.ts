/**
 * Integration tests — Event Handler form: every action type and condition.
 *
 * Each test builds a handler through the Event tab, saves it against the real
 * server, then checks two things:
 *   1. what the server stored (`GET /api/event`), so a miswired field fails
 *      here rather than silently saving the template default; and
 *   2. that reopening the handler shows the same values in the form.
 *
 * Runtime behaviour (an event actually firing the action) is not covered:
 * the server only picks up new handlers on a 60s refresh
 * (DefaultEventQueueManager.refreshEventQueues), and the Postgres indexer in
 * this stack does not persist event executions (see event-monitor.spec.ts).
 */

import type { Locator, Page } from "@playwright/test";
import { expect, test } from "../coverage-fixture";
import {
  deleteEventHandler,
  getEventHandlerByName,
  type EventHandlerDef,
} from "./api-client";

const RUN_ID = Date.now();
const created: string[] = [];

const handlerName = (slug: string) => {
  const name = `e2e_eh_${slug}_${RUN_ID}`;
  created.push(name);
  return name;
};

test.afterAll(async () => {
  await Promise.all(
    created.map((name) => deleteEventHandler(name).catch(() => {})),
  );
});

// ── Helpers ───────────────────────────────────────────────────────────────────

const section = (page: Page, key: string) =>
  page.locator(`#event-handler-section-${key}`);

const actionCard = (page: Page, index = 0) =>
  page.locator(`#event-handler-action-${index}`);

/** Opens a new handler and fills the fields every test needs. */
async function startNewHandler(page: Page, name: string, event: string) {
  await page.goto("/newEventHandlerDef");
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#event-handler-form-wrapper")).toBeVisible();

  await page.locator("#event-name-input").fill(name);
  await page
    .locator("#event-description-field")
    .fill("Created by Playwright E2E test — safe to delete");
  await page.locator("#event-string-input").fill(event);
}

/**
 * The new-handler template starts with a Complete Task action. Add `label`
 * from the menu (it is prepended, so it lands at index 0), then remove the
 * template action that moved to index 1.
 */
async function replaceTemplateAction(page: Page, label: string) {
  await section(page, "actions")
    .getByRole("button", { name: /Add action/i })
    .click();
  await page.getByRole("menuitem", { name: new RegExp(`^${label}`) }).click();
  await actionCard(page, 1).getByLabel("Remove action").click();
  await expect(actionCard(page, 1)).toHaveCount(0);
}

/**
 * Replaces the Condition editor's text. Monaco has no fillable input, and
 * select-all shortcuts don't reach its EditContext under Playwright on macOS,
 * so go through the editor API — setValue still fires the form's onChange.
 */
async function setCondition(page: Page, condition: string) {
  const editor = section(page, "condition").locator(".monaco-editor").first();
  await expect(editor).toBeVisible();
  await editor.evaluate((node, value) => {
    const monaco = (
      window as unknown as {
        monaco?: {
          editor: {
            getEditors(): Array<{
              getDomNode(): HTMLElement | null;
              setValue(v: string): void;
            }>;
          };
        };
      }
    ).monaco;
    const target = monaco?.editor
      .getEditors()
      .find(
        (e) => e.getDomNode()?.contains(node) || node.contains(e.getDomNode()!),
      );
    if (!target) throw new Error("Condition Monaco editor not found");
    target.setValue(value);
  }, condition);
}

/** Adds one key/value row to the key/value form titled `title`. */
async function addKeyValue(
  card: Locator,
  title: string,
  key: string,
  value: string,
) {
  await card.getByRole("button", { name: "Add parameter" }).first().click();
  const keyInput = card.getByLabel("Key", { exact: true }).last();
  await keyInput.fill(key);
  await card.getByLabel("Value", { exact: true }).last().fill(value);
  // Group title only anchors intent in failure output.
  await expect(card.getByText(title).first()).toBeVisible();
}

/** Save → confirm diff → wait for the success toast and saved URL. */
async function saveHandler(page: Page, name: string) {
  await expect(page.locator("#save-event-handler")).toBeEnabled();
  await page.locator("#save-event-handler").click();
  await expect(page.locator("#confirm-save-event-handler")).toBeVisible();
  await page.locator("#confirm-save-event-handler").click();
  await expect(page.getByText("Event handler saved successfully.")).toBeVisible(
    { timeout: 15_000 },
  );
  await expect(page).toHaveURL(
    new RegExp(`/eventHandlerDef/${encodeURIComponent(name)}`),
    { timeout: 15_000 },
  );
}

async function fetchSaved(name: string): Promise<EventHandlerDef> {
  const saved = await getEventHandlerByName(name);
  expect(saved, `handler ${name} should exist on the server`).toBeDefined();
  return saved!;
}

/** Reloads the saved handler so the form is populated from the server. */
async function reopen(page: Page, name: string) {
  await page.goto(`/eventHandlerDef/${encodeURIComponent(name)}`);
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#event-name-input")).toHaveValue(name);
}

// ── Action types ──────────────────────────────────────────────────────────────

test.describe("event handler action types", () => {
  test("complete_task by workflow ID + task reference name, with output", async ({
    page,
  }) => {
    const name = handlerName("complete_task");
    await startNewHandler(page, name, `conductor:e2e_complete_${RUN_ID}`);

    const card = actionCard(page);
    await card
      .getByLabel("Workflow ID", { exact: true })
      .fill("${event.payload.workflowId}");
    await card.getByLabel("Task reference name").fill("wait_for_approval");
    await addKeyValue(card, "Output", "approvedBy", "e2e");
    await saveHandler(page, name);

    const [action] = (await fetchSaved(name)).actions;
    expect(action.action).toBe("complete_task");
    expect(action.complete_task).toMatchObject({
      workflowId: "${event.payload.workflowId}",
      taskRefName: "wait_for_approval",
      output: { approvedBy: "e2e" },
    });

    await reopen(page, name);
    await expect(
      actionCard(page).getByLabel("Workflow ID", { exact: true }),
    ).toHaveValue("${event.payload.workflowId}");
    await expect(
      actionCard(page).getByLabel("Task reference name"),
    ).toHaveValue("wait_for_approval");
  });

  test("fail_task by task ID", async ({ page }) => {
    const name = handlerName("fail_task");
    await startNewHandler(page, name, `conductor:e2e_fail_${RUN_ID}`);
    await replaceTemplateAction(page, "Fail Task");

    const card = actionCard(page);
    await card.locator("#task-id-radio-button").check();
    await card
      .getByLabel("Task ID", { exact: true })
      .fill("${event.payload.taskId}");
    await saveHandler(page, name);

    const [action] = (await fetchSaved(name)).actions;
    expect(action.action).toBe("fail_task");
    const failTask = action.fail_task as Record<string, unknown>;
    expect(failTask.taskId).toBe("${event.payload.taskId}");
    // Task ID mode drops the workflow/ref pair rather than sending both.
    expect(failTask.workflowId ?? null).toBeNull();
    expect(failTask.taskRefName ?? null).toBeNull();

    await reopen(page, name);
    await expect(
      actionCard(page).locator("#task-id-radio-button"),
    ).toBeChecked();
    await expect(
      actionCard(page).getByLabel("Task ID", { exact: true }),
    ).toHaveValue("${event.payload.taskId}");
  });

  test("terminate_workflow", async ({ page }) => {
    const name = handlerName("terminate_workflow");
    await startNewHandler(page, name, `conductor:e2e_terminate_${RUN_ID}`);
    await replaceTemplateAction(page, "Terminate Workflow");

    const card = actionCard(page);
    await card
      .getByLabel("Workflow ID", { exact: true })
      .fill("${event.payload.workflowId}");
    await card.getByLabel("Termination reason").fill("cancelled upstream");
    await saveHandler(page, name);

    const [action] = (await fetchSaved(name)).actions;
    expect(action.action).toBe("terminate_workflow");
    expect(action.terminate_workflow).toEqual({
      workflowId: "${event.payload.workflowId}",
      terminationReason: "cancelled upstream",
    });

    await reopen(page, name);
    await expect(actionCard(page).getByLabel("Termination reason")).toHaveValue(
      "cancelled upstream",
    );
  });

  test("update_workflow_variables with append and a variable", async ({
    page,
  }) => {
    const name = handlerName("update_vars");
    await startNewHandler(page, name, `conductor:e2e_update_${RUN_ID}`);
    await replaceTemplateAction(page, "Update Variables");

    const card = actionCard(page);
    await card
      .getByLabel("Workflow ID", { exact: true })
      .fill("${event.payload.workflowId}");
    await card
      .getByLabel("Append List Variables (instead of replacing)")
      .check();
    await addKeyValue(card, "Output", "lastEvent", "${event.payload.id}");
    await saveHandler(page, name);

    const [action] = (await fetchSaved(name)).actions;
    expect(action.action).toBe("update_workflow_variables");
    expect(action.update_workflow_variables).toMatchObject({
      workflowId: "${event.payload.workflowId}",
      appendArray: true,
      variables: { lastEvent: "${event.payload.id}" },
    });

    await reopen(page, name);
    await expect(
      actionCard(page).getByLabel(
        "Append List Variables (instead of replacing)",
      ),
    ).toBeChecked();
  });

  test("start_workflow with name, version and correlation ID", async ({
    page,
  }) => {
    const name = handlerName("start_workflow");
    await startNewHandler(page, name, `conductor:e2e_start_wf_${RUN_ID}`);
    await replaceTemplateAction(page, "Start Workflow");

    const card = actionCard(page);
    // Free-solo autocompletes commit their text on blur.
    const wfName = card.getByLabel("Workflow name");
    await wfName.fill("e2e_target_workflow");
    await wfName.blur();
    const wfVersion = card.getByLabel("Workflow version");
    await wfVersion.fill("2");
    await wfVersion.blur();
    await card
      .getByLabel("Workflow correlation id")
      .fill("${event.payload.orderId}");
    await saveHandler(page, name);

    const [action] = (await fetchSaved(name)).actions;
    expect(action.action).toBe("start_workflow");
    expect(action.start_workflow).toMatchObject({
      name: "e2e_target_workflow",
      // StartWorkflow.version is an Integer on the server.
      version: 2,
      correlationId: "${event.payload.orderId}",
    });

    await reopen(page, name);
    await expect(actionCard(page).getByLabel("Workflow name")).toHaveValue(
      "e2e_target_workflow",
    );
  });

  test("start_workflow defaults to the latest version", async ({ page }) => {
    const name = handlerName("start_workflow_latest");
    await startNewHandler(page, name, `conductor:e2e_start_latest_${RUN_ID}`);
    await replaceTemplateAction(page, "Start Workflow");

    const card = actionCard(page);
    await expect(card.getByLabel("Workflow version")).toHaveValue("Latest");
    const wfName = card.getByLabel("Workflow name");
    await wfName.fill("e2e_target_workflow");
    await wfName.blur();
    await saveHandler(page, name);

    // No version on the server means "latest at the time the event fires".
    const [action] = (await fetchSaved(name)).actions;
    expect(action.start_workflow).toMatchObject({
      name: "e2e_target_workflow",
    });
    expect(action.start_workflow?.version ?? null).toBeNull();

    await reopen(page, name);
    await expect(actionCard(page).getByLabel("Workflow version")).toHaveValue(
      "Latest",
    );
  });

  test("start_agent with name, prompt and session ID", async ({ page }) => {
    const name = handlerName("start_agent");
    await startNewHandler(page, name, `conductor:e2e_start_agent_${RUN_ID}`);
    await replaceTemplateAction(page, "Start Agent");

    const card = actionCard(page);
    const agentName = card.getByLabel("Agent name");
    await agentName.fill("e2e_support_agent");
    await agentName.blur();
    await card.getByLabel("Prompt").fill("Summarise ${event.payload.ticket}");
    await card.getByLabel("Session ID").fill("${event.payload.sessionId}");
    await saveHandler(page, name);

    const [action] = (await fetchSaved(name)).actions;
    expect(action.action).toBe("start_agent");
    expect(action.start_agent).toMatchObject({
      name: "e2e_support_agent",
      prompt: "Summarise ${event.payload.ticket}",
      sessionId: "${event.payload.sessionId}",
    });

    await reopen(page, name);
    await expect(actionCard(page).getByLabel("Agent name")).toHaveValue(
      "e2e_support_agent",
    );
  });

  test("several actions save in order", async ({ page }) => {
    const name = handlerName("multi_action");
    await startNewHandler(page, name, `conductor:e2e_multi_${RUN_ID}`);

    // Each add is prepended, so the saved order is the reverse of adding.
    for (const label of ["Fail Task", "Terminate Workflow"]) {
      await section(page, "actions")
        .getByRole("button", { name: /Add action/i })
        .click();
      await page
        .getByRole("menuitem", { name: new RegExp(`^${label}`) })
        .click();
    }
    await saveHandler(page, name);

    const saved = await fetchSaved(name);
    expect(saved.actions.map((a) => a.action)).toEqual([
      "terminate_workflow",
      "fail_task",
      "complete_task",
    ]);
  });
});

// ── Conditions ────────────────────────────────────────────────────────────────

test.describe("event handler conditions", () => {
  test("saves a condition with the template's javascript evaluator", async ({
    page,
  }) => {
    const name = handlerName("cond_javascript");
    const condition = "$.amount > 100 && $.currency === 'USD'";
    await startNewHandler(page, name, `conductor:e2e_cond_${RUN_ID}`);
    await setCondition(page, condition);
    await saveHandler(page, name);

    const saved = await fetchSaved(name);
    // The form has no evaluator control; the template's value is kept.
    expect(saved.evaluatorType).toBe("javascript");
    expect(saved.condition).toBe(condition);

    await reopen(page, name);
    await expect(
      section(page, "condition").locator(".view-lines").first(),
    ).toHaveText(condition.replace(/ /g, "\u00a0"));
  });

  test("saves an empty condition (actions run on every event)", async ({
    page,
  }) => {
    const name = handlerName("cond_empty");
    await startNewHandler(page, name, `conductor:e2e_cond_empty_${RUN_ID}`);
    await setCondition(page, "");
    await saveHandler(page, name);

    const saved = await fetchSaved(name);
    expect(saved.condition ?? "").toBe("");
  });

  test("inactive handlers save active: false", async ({ page }) => {
    const name = handlerName("inactive");
    await startNewHandler(page, name, `conductor:e2e_inactive_${RUN_ID}`);
    await section(page, "details").getByLabel("Active").uncheck();
    await saveHandler(page, name);

    expect((await fetchSaved(name)).active).toBe(false);
  });
});
