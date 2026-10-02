/**
 * Integration tests — executing from the workflow definition editor.
 *
 * Verifies that workflows with declared inputs stop on the Run tab before
 * execution, while workflows without inputs retain the one-click Execute flow.
 */

import { expect, test } from "../coverage-fixture";
import {
  createWorkflowDef,
  deleteWorkflowDef,
  terminateWorkflow,
  type WorkflowDef,
} from "./api-client";

const RUN_ID = Date.now();
const startedWorkflowIds: string[] = [];

const workflowWithInputs: WorkflowDef = {
  name: `e2e_definition_execute_inputs_${RUN_ID}`,
  version: 1,
  description: "Definition Execute test with inputs — safe to delete",
  inputParameters: ["orderId"],
  tasks: [
    {
      name: "capture_input",
      taskReferenceName: "capture_input_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        orderId: "${workflow.input.orderId}",
      },
    },
  ],
};

const workflowWithoutInputs: WorkflowDef = {
  name: `e2e_definition_execute_no_inputs_${RUN_ID}`,
  version: 1,
  description: "Definition Execute test without inputs — safe to delete",
  tasks: [
    {
      name: "complete",
      taskReferenceName: "complete_ref",
      type: "SET_VARIABLE",
      inputParameters: {
        completed: true,
      },
    },
  ],
};

const isStartWorkflowRequest = (url: string, method: string) =>
  method === "POST" && new URL(url).pathname === "/api/workflow";

test.beforeAll(async () => {
  await createWorkflowDef(workflowWithInputs);
  await createWorkflowDef(workflowWithoutInputs);
});

test.afterAll(async () => {
  await Promise.allSettled(
    startedWorkflowIds.map((id) => terminateWorkflow(id)),
  );
  await deleteWorkflowDef(workflowWithInputs.name).catch(() => {});
  await deleteWorkflowDef(workflowWithoutInputs.name).catch(() => {});
});

test("Execute opens the Run tab without starting a workflow when inputs are declared", async ({
  page,
}) => {
  let startRequestCount = 0;
  page.on("request", (request) => {
    if (isStartWorkflowRequest(request.url(), request.method())) {
      startRequestCount += 1;
    }
  });

  await page.goto(`/workflowDef/${workflowWithInputs.name}/1`);
  await page.waitForLoadState("networkidle");
  await page.locator("#head-action-run-btn").click();

  const runTab = page.getByRole("tab", { name: "Run" });
  await expect(runTab).toHaveAttribute("aria-selected", "true");
  await expect(
    page.getByRole("textbox", { name: "Editor content" }).first(),
  ).toBeVisible({ timeout: 30_000 });
  await expect(page.locator(".monaco-editor.focused").first()).toBeVisible();
  expect(startRequestCount).toBe(0);
});

test("Run-tab Execute starts the workflow with the edited input values", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${workflowWithInputs.name}/1`);
  await page.waitForLoadState("networkidle");
  await page.locator("#head-action-run-btn").click();

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
            .some((model) => model.getValue().includes("orderId")) ?? false
        );
      }),
    )
    .toBe(true);

  const expectedInput = { orderId: "ORD-E2E-42" };
  await page.evaluate(
    (inputJson) => {
      const monaco = (
        window as unknown as {
          monaco: {
            editor: {
              getModels: () => Array<{
                getValue: () => string;
                setValue: (value: string) => void;
              }>;
            };
          };
        }
      ).monaco;
      const inputModel = monaco.editor
        .getModels()
        .find((model) => model.getValue().includes("orderId"));
      if (!inputModel) {
        throw new Error("No Monaco model found for workflow input parameters");
      }
      inputModel.setValue(inputJson);
    },
    JSON.stringify(expectedInput, null, 2),
  );

  const startResponse = page.waitForResponse((response) =>
    isStartWorkflowRequest(response.url(), response.request().method()),
  );
  await page.locator("#run-tab-execute-btn").click();

  const response = await startResponse;
  const request = response.request();
  startedWorkflowIds.push((await response.text()).trim());
  expect(request.postDataJSON()).toMatchObject({
    name: workflowWithInputs.name,
    version: 1,
    input: expectedInput,
  });
  await expect(page).toHaveURL(/\/execution\//, { timeout: 30_000 });
});

test("Execute starts a workflow immediately when no inputs are declared", async ({
  page,
}) => {
  await page.goto(`/workflowDef/${workflowWithoutInputs.name}/1`);
  await page.waitForLoadState("networkidle");

  const startResponse = page.waitForResponse((response) =>
    isStartWorkflowRequest(response.url(), response.request().method()),
  );
  await page.locator("#head-action-run-btn").click();

  const response = await startResponse;
  const request = response.request();
  startedWorkflowIds.push((await response.text()).trim());
  expect(request.postDataJSON()).toMatchObject({
    name: workflowWithoutInputs.name,
    version: 1,
    input: {},
  });
  await expect(page).toHaveURL(/\/execution\//, { timeout: 30_000 });
});
