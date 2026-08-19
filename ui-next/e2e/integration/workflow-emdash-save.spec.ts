/**
 * Integration tests — em-dash round-trip through the UI save and clone paths.
 *
 * Verifies that a workflow containing em-dash characters (U+2014) in task
 * inputParameters can be saved and cloned via the UI without the characters
 * being corrupted.
 *
 * Background: some WAF/proxy deployments strip C1 bytes (0x80-0x9F) from the
 * raw request body. The em-dash is encoded as E2 80 94 in UTF-8; losing 0x80
 * produces invalid UTF-8 that Jackson rejects. fetchWithContext now applies
 * asciiSafeJson() to valid JSON bodies before sending, escaping non-ASCII
 * chars as \uXXXX sequences that survive any byte-stripping layer.
 *
 * The customer reported the issue specifically on the clone path (UI) while
 * direct API calls worked fine — because only browser traffic goes through
 * the corporate WAF/proxy.
 */

import { expect, test } from "../coverage-fixture";
import {
  createWorkflowDef,
  deleteWorkflowDef,
  getWorkflowDef,
  type WorkflowDef,
} from "./api-client";

const RUN_ID = Date.now();
const WF_NAME = `e2e_emdash_save_${RUN_ID}`;
const WF_CLONE_NAME = `e2e_emdash_clone_${RUN_ID}`;

const EM_DASH = "—";

const WF_DEF: WorkflowDef = {
  name: WF_NAME,
  version: 1,
  description: `Em-dash test workflow ${EM_DASH} safe to delete`,
  tasks: [
    {
      name: "set_var",
      taskReferenceName: "set_var_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        // em-dash characters in a prompt — this is the field LinkedIn's
        // workflows use and the trigger for the WAF corruption issue.
        prompt: `Analyze the data ${EM_DASH} key metrics ${EM_DASH} return JSON`,
        result: "${workflow.input.value}",
      },
    },
  ],
  inputParameters: ["value"],
};

// Monaco loads its editor runtime asynchronously; allow extra time.
const MONACO_TIMEOUT_MS = 30_000;

test.beforeAll(async () => {
  await createWorkflowDef(WF_DEF);
});

test.afterAll(async () => {
  await deleteWorkflowDef(WF_NAME).catch(() => {});
  await deleteWorkflowDef(WF_CLONE_NAME).catch(() => {});
});

test("em-dash in task inputParameters survives save via the UI editor", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${WF_NAME}/1`);
  await page.waitForLoadState("networkidle");

  // Switch to Code tab — confirms em-dash is present in the editor before saving.
  await page.getByRole("tab", { name: "Code" }).click();
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toContainText(EM_DASH, { timeout: MONACO_TIMEOUT_MS });

  // Click Save → Confirm.
  await page.locator('[data-testid="workflow-definition-save-button"]').click();
  await page.locator("#confirm-saving-btn").click();

  // Wait for the confirm panel to disappear.
  await expect(page.locator("#confirm-saving-btn")).not.toBeVisible({
    timeout: 10_000,
  });
  await page.waitForLoadState("networkidle");

  // Re-fetch via API and assert the em-dash round-tripped correctly.
  const saved = await getWorkflowDef(WF_NAME, 1);
  const prompt = (saved.tasks[0].inputParameters as Record<string, string>)
    .prompt;

  expect(prompt).toContain(EM_DASH);
  expect(prompt).not.toContain("\\u2014");
});

test("em-dash in task inputParameters survives clone via the UI", async ({
  page,
}) => {
  // Navigate to the workflow list and trigger the Clone dialog for our workflow.
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  // Scope to the row for our workflow and click its Clone icon button.
  const row = page.locator("tr").filter({ hasText: WF_NAME });
  await row.getByTitle("Clone Workflow").click();

  // The clone dialog opens — clear the name field and enter the clone name.
  await page.locator("#workflow-name-field").fill(WF_CLONE_NAME);

  // Click Clone to submit — this sends the cloned workflow JSON through
  // fetchWithContext → asciiSafeJson, which is the path the customer reported.
  await page.locator("#confirm-clone-btn").click();

  // Wait for the dialog to close.
  await expect(page.locator("#confirm-clone-btn")).not.toBeVisible({
    timeout: 10_000,
  });
  await page.waitForLoadState("networkidle");

  // Re-fetch the cloned workflow via API and assert the em-dash survived.
  const cloned = await getWorkflowDef(WF_CLONE_NAME, 1);
  const prompt = (cloned.tasks[0].inputParameters as Record<string, string>)
    .prompt;

  expect(prompt).toContain(EM_DASH);
  expect(prompt).not.toContain("\\u2014");
});
