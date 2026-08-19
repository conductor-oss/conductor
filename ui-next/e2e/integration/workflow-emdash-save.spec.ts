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

  // Switch to Code tab — confirms em-dash is present in the editor.
  await page.getByRole("tab", { name: "Code" }).click();
  await expect(
    page.locator("#editor-panel-tab-content #code-tab"),
  ).toContainText(EM_DASH, { timeout: MONACO_TIMEOUT_MS });

  // The save button is disabled until the editor is dirty (madeChanges=true).
  // Dirty it by appending a trailing space to the description via Monaco's JS
  // API — @monaco-editor/react sets window.monaco after the CDN bundle loads.
  await page.waitForFunction(
    () => !!(window as any).monaco?.editor?.getModels?.()?.length,
    { timeout: MONACO_TIMEOUT_MS },
  );
  await page.evaluate(() => {
    const models = (window as any).monaco.editor.getModels();
    const model = models[0];
    const obj = JSON.parse(model.getValue());
    obj.description = (obj.description ?? "") + " ";
    model.setValue(JSON.stringify(obj, null, 2));
  });

  // Wait for XState to pick up the model change and enable the save button.
  await expect(
    page.locator('[data-testid="workflow-definition-save-button"]'),
  ).toBeEnabled({ timeout: 5_000 });

  // Intercept the save request so we can inspect the wire body.
  const saveRequest = page.waitForRequest(
    (req) =>
      req.url().includes("/api/metadata/workflow") &&
      (req.method() === "POST" || req.method() === "PUT"),
  );

  // Click Save → Confirm.
  await page.locator('[data-testid="workflow-definition-save-button"]').click();
  await page.locator("#confirm-saving-btn").click();

  // Capture the request body before it reaches the server.
  const req = await saveRequest;
  const body = req.postData() ?? "";

  // The body must use — (not raw em-dash bytes) — this is what
  // asciiSafeJson() produces and what survives WAF C1-byte stripping.
  expect(body).toContain("\\u2014");
  expect(body).not.toContain(EM_DASH);

  // Also verify the server decoded it correctly: re-fetch and check the value.
  await expect(page.locator("#confirm-saving-btn")).not.toBeVisible({
    timeout: 10_000,
  });
  await page.waitForLoadState("networkidle");

  const saved = await getWorkflowDef(WF_NAME, 1);
  const prompt = (saved.tasks[0].inputParameters as Record<string, string>)
    .prompt;
  expect(prompt).toContain(EM_DASH);
});

test("em-dash in task inputParameters survives clone via the UI", async ({
  page,
}) => {
  await page.goto("/workflowDef");
  await page.waitForLoadState("networkidle");

  // The clone IconButton has no id; locate it as the sibling immediately
  // after the run button (which does carry id="run-{name}-btn").
  // MUI Tooltip does not add a title attribute to its child, so getByTitle
  // does not work here.
  await page.locator(`#run-${WF_NAME}-btn + button`).click();

  // The clone dialog opens — clear the name field and enter the clone name.
  await page.locator("#workflow-name-field").fill(WF_CLONE_NAME);

  // Intercept the clone POST to verify wire-level encoding.
  const cloneRequest = page.waitForRequest(
    (req) =>
      req.url().includes("/api/metadata/workflow") &&
      req.method() === "POST",
  );

  await page.locator("#confirm-clone-btn").click();

  const req = await cloneRequest;
  const body = req.postData() ?? "";

  expect(body).toContain("\\u2014");
  expect(body).not.toContain(EM_DASH);

  // Wait for dialog to close then verify the clone round-tripped correctly.
  await expect(page.locator("#confirm-clone-btn")).not.toBeVisible({
    timeout: 10_000,
  });
  await page.waitForLoadState("networkidle");

  const cloned = await getWorkflowDef(WF_CLONE_NAME, 1);
  const prompt = (cloned.tasks[0].inputParameters as Record<string, string>)
    .prompt;
  expect(prompt).toContain(EM_DASH);
});
