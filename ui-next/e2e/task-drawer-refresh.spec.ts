import { expect, test } from "@playwright/test";
import { mockCommonApis } from "./helpers/mockApi";

// Exact public task identity from the reported screen; no invocation payloads.
const workflowId = "0808dc81-d8fd-11f1-9591-2653c9955b17";
const taskId = "0ad8c563-d8fd-11f1-9591-2653c9955b17";
const definitions = ["List Functions", "Invoke Function"].map((name) => ({
  name,
  taskReferenceName: `${name}_ref`,
  type: "CONNECTED_APP",
  inputParameters: {},
}));

test("top Refresh hydrates a Pending graph drawer after task instances arrive", async ({
  page,
}) => {
  await mockCommonApis(page);
  let scheduled = false;
  await page.route(`**/api/workflow/${workflowId}?**`, (route) =>
    route.fulfill({
      json: {
        workflowId,
        status: "COMPLETED",
        workflowType: "NewWorkflow_165ce",
        workflowVersion: 1,
        startTime: 1793871674000,
        endTime: 1793871676000,
        input: {},
        output: {},
        workflowDefinition: {
          name: "NewWorkflow_165ce",
          version: 1,
          schemaVersion: 2,
          tasks: definitions,
        },
        tasks: scheduled
          ? definitions.map((workflowTask, index) => ({
              taskId:
                index === 1 ? taskId : "08127972-d8fd-11f1-9591-2653c9955b17",
              taskType: "CONNECTED_APP",
              status: "COMPLETED",
              referenceTaskName: workflowTask.taskReferenceName,
              workflowTask,
              seq: index + 1,
              retryCount: 0,
              inputData: {},
              outputData: {},
              scheduledTime: 1793871674000,
              startTime: 1793871674200,
              endTime: 1793871676000,
            }))
          : [],
      },
    }),
  );
  await page.goto(`/execution/${workflowId}`);
  await page.getByText("Invoke Function", { exact: true }).click();
  await expect(page.getByText("Pending", { exact: true })).toHaveCount(2);
  scheduled = true;
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(
    page.getByText("Task execution id", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Pending", { exact: true })).toHaveCount(0);
  await expect(page.getByText("Completed", { exact: true })).toHaveCount(3);
  await expect(page).toHaveURL(new RegExp(`taskId=${taskId}`));
  await page.screenshot({
    path: test.info().outputPath("graph-drawer-completed.png"),
  });
});
