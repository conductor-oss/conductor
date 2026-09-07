/**
 * Integration test — failed save of a workflow with an unresolved task
 * reference must keep the server error in the Error Inspector alongside
 * the client-side "Task missing references" warning.
 *
 * The server returns HTTP 400 with `{ message, validationErrors: [] }`.
 * The UI used to drop that error on the post-save flow re-render.
 */

import { expect, test } from "../coverage-fixture";
import { deleteWorkflowDef } from "./api-client";

const RUN_ID = Date.now();
const WF_NAME = `e2e_wf_unresolved_ref_${RUN_ID}`;

const MONACO_TIMEOUT_MS = 30_000;

const WORKFLOW = {
  name: WF_NAME,
  description: "E2E unresolved task reference — safe to delete",
  version: 1,
  tasks: [
    {
      name: "set_var",
      taskReferenceName: "test_simple_task_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        value: "${nonexistent_task_ref.output.result}",
      },
    },
  ],
  inputParameters: [],
  outputParameters: {},
  schemaVersion: 2,
  restartable: true,
  ownerEmail: "e2e@orkes.io",
  timeoutSeconds: 0,
};

test.afterAll(async () => {
  await deleteWorkflowDef(WF_NAME).catch(() => {});
});

test("failed save keeps the server error next to Task missing references", async ({
  page,
}) => {
  await page.goto("/newWorkflowDef");
  await page.waitForLoadState("networkidle");
  await expect(page.locator("#workflow-name-display")).toBeVisible();

  await page.getByRole("tab", { name: "Code" }).click();
  await expect(page.locator("#editor-panel-tab-content #code-tab")).toBeVisible(
    { timeout: MONACO_TIMEOUT_MS },
  );

  await page.waitForFunction(
    () => !!(window as any).monaco?.editor?.getModels?.()?.length,
    { timeout: MONACO_TIMEOUT_MS },
  );
  await page.evaluate((workflow) => {
    const models = (window as any).monaco.editor.getModels();
    models[0].setValue(JSON.stringify(workflow, null, 2));
  }, WORKFLOW);

  await expect(
    page.locator('[data-testid="workflow-definition-save-button"]'),
  ).toBeEnabled({ timeout: 10_000 });

  const saveResponse = page.waitForResponse(
    (res) =>
      res.url().includes("/api/metadata/workflow") &&
      (res.request().method() === "POST" || res.request().method() === "PUT"),
  );

  await page.locator('[data-testid="workflow-definition-save-button"]').click();
  await page.locator("#confirm-saving-btn").click();

  const response = await saveResponse;
  expect(response.status()).toBe(400);

  const inspector = page.locator("#error-inspector-container");
  await expect(inspector).toBeVisible({ timeout: 10_000 });
  await expect(inspector).toContainText("Workflow was not saved", {
    timeout: 10_000,
  });
  await expect(inspector).toContainText("not defined in workflow definition");
  await expect(inspector).toContainText("Task missing references");
});
