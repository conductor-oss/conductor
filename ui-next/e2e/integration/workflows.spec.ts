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
  await expect(page.locator("#main-content")).toBeVisible();
});

test("navigating directly to a versioned definition URL opens the editor", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_EDITOR.name}/1`);
  await page.waitForLoadState("networkidle");

  // The page title / heading should reference the workflow name.
  await expect(page.getByText(WF_EDITOR.name).first()).toBeVisible();
});

test("navigating to /workflowDef/new opens an empty editor", async ({
  page,
}) => {
  await page.goto("/workflowDef/new");
  await page.waitForLoadState("networkidle");

  await expect(page).toHaveURL(/\/workflowDef\/new/);
  await expect(page.locator("#main-content")).toBeVisible();
});
