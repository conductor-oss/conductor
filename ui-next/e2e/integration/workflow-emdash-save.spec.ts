/**
 * Integration test — em-dash round-trip through the UI save path.
 *
 * Verifies that a workflow containing em-dash characters (U+2014) in task
 * inputParameters can be opened in the editor and saved via the UI without
 * the characters being corrupted.
 *
 * Background: some WAF/proxy deployments strip C1 bytes (0x80-0x9F) from the
 * raw request body. The em-dash is encoded as E2 80 94 in UTF-8; losing 0x80
 * produces invalid UTF-8 that Jackson rejects. fetchWithContext now applies
 * asciiSafeJson() to JSON bodies before sending, escaping non-ASCII chars as
 * \uXXXX sequences that survive any byte-stripping layer.
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
});

test("em-dash in task inputParameters survives save via the UI editor", async ({
  page,
}) => {
  // Open the workflow in the editor.
  await page.goto(`/workflowDef/${WF_NAME}/1`);
  await page.waitForLoadState("networkidle");

  // Switch to the Code tab so Monaco loads the full JSON (confirms the
  // definition with em-dashes is in the editor before we save).
  await page.getByRole("tab", { name: "Code" }).click();
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toContainText(EM_DASH, { timeout: MONACO_TIMEOUT_MS });

  // Click the top-level Save button.
  await page.locator('[data-testid="workflow-definition-save-button"]').click();

  // A confirmation dialog appears — click Confirm to actually send the request.
  await page.locator("#confirm-saving-btn").click();

  // Wait for the save to complete: the confirm panel disappears and the page
  // returns to a stable state.
  await expect(page.locator("#confirm-saving-btn")).not.toBeVisible({
    timeout: 10_000,
  });
  await page.waitForLoadState("networkidle");

  // Re-fetch the workflow directly from the API and assert the em-dash
  // survived the round-trip through fetchWithContext → asciiSafeJson → server.
  const saved = await getWorkflowDef(WF_NAME, 1);
  const prompt = (saved.tasks[0].inputParameters as Record<string, string>)
    .prompt;

  expect(prompt).toContain(EM_DASH);
  expect(prompt).not.toContain("\\u2014");
});
