/**
 * Integration tests — Workflow Definitions
 *
 * Creates real workflow definitions via the API and verifies the UI reflects
 * them correctly: the list page, clicking through to the editor, and
 * confirming versioned definitions resolve to the right URL.
 */

import { expect, test } from "@playwright/test";
import {
  createWorkflowDef,
  deleteWorkflowDef,
  type WorkflowDef,
} from "./api-client";

// Generate a unique name per test run so parallel runs don't collide.
const RUN_ID = Date.now();

function makeWorkflowDef(suffix: string): WorkflowDef {
  return {
    name: `e2e_wf_${suffix}_${RUN_ID}`,
    version: 1,
    description: "Created by Playwright E2E test — safe to delete",
    tasks: [
      {
        // SET_VARIABLE is a built-in system task that completes immediately,
        // so workflows using it reach COMPLETED state without a worker.
        name: "set_var",
        taskReferenceName: "set_var_ref",
        type: "SET_VARIABLE",
        inputParameters: {
          result: "${workflow.input.value}",
        },
      },
    ],
    inputParameters: ["value"],
  };
}

// ── Fixtures ──────────────────────────────────────────────────────────────────

const WF_LIST = makeWorkflowDef("list");
const WF_EDITOR = makeWorkflowDef("editor");

test.beforeAll(async () => {
  await createWorkflowDef(WF_LIST);
  await createWorkflowDef(WF_EDITOR);
});

test.afterAll(async () => {
  await deleteWorkflowDef(WF_LIST.name).catch(() => {});
  await deleteWorkflowDef(WF_EDITOR.name).catch(() => {});
});

// ── Tests ─────────────────────────────────────────────────────────────────────

test("workflow definition appears in the /workflowDef list", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page.getByText(WF_LIST.name)).toBeVisible();
});

test("workflow definition description is shown in the list", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  // Description column should display the description we set.
  await expect(
    page.getByText("Created by Playwright E2E test — safe to delete").first(),
  ).toBeVisible();
});

test("clicking a workflow definition opens the definition editor", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  // Use a link locator scoped to the main content table so we don't
  // accidentally click a heading or breadcrumb with the same text.
  await page.locator("#main-content").getByText(WF_EDITOR.name).first().click();

  await expect(page).toHaveURL(new RegExp(`/workflowDef/${WF_EDITOR.name}`));
  await page.waitForLoadState("networkidle");

  await expect(page.locator("#main-content")).toBeVisible();

  // Workflow name and version selector should be visible in the editor.
  await expect(page.locator("#workflow-name-display")).toBeVisible();
  // The list links to /workflowDef/<name> without a version, so the selector
  // shows "Latest version" (its labelOnEmpty) rather than "Version 1".
  await expect(page.getByText("Latest version")).toBeVisible();

  // Description set at creation time should appear in the editor.
  await expect(
    page.getByText("Created by Playwright E2E test — safe to delete"),
  ).toBeVisible();

  // The task reference name should appear in the task graph / task list.
  await expect(page.getByText("set_var_ref")).toBeVisible();
});

test("navigating directly to a versioned definition URL opens the editor", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_EDITOR.name}/1`);
  await page.waitForLoadState("networkidle");

  // The page title / heading should reference the workflow name.
  await expect(page.getByText(WF_EDITOR.name).first()).toBeVisible();
});

test("clicking a task node in the flow diagram opens the task editor panel", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_EDITOR.name}/1`);
  await page.waitForLoadState("networkidle");

  // The flow diagram renders the task node with the task reference name.
  // Clicking it fires SELECT_NODE_EVT which auto-switches to the Task tab.
  await page.getByText("set_var_ref").first().click();

  // The task form panel should now be visible.
  await expect(page.locator("#maybe-task-form")).toBeVisible();

  // The task type badge should show SET_VARIABLE in the task form panel.
  // Scope to #maybe-task-form to avoid the identical badge inside the flow node.
  await expect(
    page.locator("#maybe-task-form").getByText("SET_VARIABLE"),
  ).toBeVisible();

  // The reference name input should be pre-populated with the task's reference name.
  await expect(
    page.locator("#task-form-header-task-reference-field"),
  ).toHaveValue("set_var_ref");
});

test("switching to the Code tab shows the workflow JSON editor", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_EDITOR.name}/1`);
  await page.waitForLoadState("networkidle");

  // Switch to the Code tab.
  await page.getByRole("tab", { name: "Code" }).click();

  // The Monaco editor container should be visible.
  // Use the id that the Box renders to avoid a loading-wrapper that also
  // carries the same data-cy attribute (#editor-panel-tab-content #code-tab
  // is the unique alias Playwright reports for this element).
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toBeVisible();

  // The Code tab button should now be selected.
  await expect(page.getByRole("tab", { name: "Code" })).toHaveAttribute(
    "aria-selected",
    "true",
  );

  // The Monaco editor renders JSON into .view-lines DOM nodes; verify the
  // workflow definition JSON is actually present in the editor content.
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toContainText("SET_VARIABLE");
});

test("navigating to /newWorkflowDef opens an empty editor", async ({
  page,
}) => {
  await page.goto("/newWorkflowDef");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/newWorkflowDef/);

  // WorkflowMetaBar only mounts once the machine reaches "ready" state.
  await expect(page.locator("#workflow-name-display")).toBeVisible();

  // The new-workflow template names the definition "NewWorkflow_<random>".
  // Matching this prefix confirms we have a fresh scaffold, not an existing def.
  await expect(page.locator("#workflow-name-display")).toHaveText(
    /NewWorkflow_/,
  );

  // Switch to the Code tab to inspect the raw JSON — the most direct way to
  // confirm the workflow is empty (tasks array has no entries).
  await page.getByRole("tab", { name: "Code" }).click();
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toBeVisible();

  // An empty workflow has "tasks": [] in its JSON. Verify this is present and
  // that no task reference names snuck in from a previously loaded definition.
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toContainText('"tasks": []');
});
