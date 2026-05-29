import React from "react";
import { test, expect } from "@playwright/experimental-ct-react";

// @ts-ignore - JS module without type declarations
import WorkflowDAG from "./WorkflowDAG";
// @ts-ignore - JS module without type declarations
import WorkflowGraph from "./WorkflowGraph";

import successData from "../../../e2e/fixtures/dynamicFork/success.json";
import oneFailedData from "../../../e2e/fixtures/dynamicFork/oneFailed.json";
import externalizedInputData from "../../../e2e/fixtures/dynamicFork/externalizedInput.json";
import notExecutedData from "../../../e2e/fixtures/dynamicFork/notExecuted.json";
import noneSpawnedData from "../../../e2e/fixtures/dynamicFork/noneSpawned.json";
import doWhileSwitchData from "../../../e2e/fixtures/doWhile/doWhileSwitch.json";

// Wrapper instantiates WorkflowDAG in the browser context (avoids serialization issues)
function WorkflowGraphTestBed({
  rawData,
  workflowDefinition,
  executionMode,
  onClickCapture,
}: {
  rawData?: object | null;
  workflowDefinition?: object;
  executionMode: boolean;
  onClickCapture?: (args: unknown) => void;
}) {
  const dag = new WorkflowDAG(rawData ?? null, workflowDefinition ?? null);
  return (
    <WorkflowGraph
      dag={dag}
      executionMode={executionMode}
      onClick={onClickCapture}
    />
  );
}

test.describe("<WorkflowGraph>", () => {
  test("Dynamic Fork - success", async ({ mount }) => {
    let clickedWith: unknown;
    const component = await mount(
      <WorkflowGraphTestBed
        rawData={successData}
        executionMode={true}
        onClickCapture={(args) => {
          clickedWith = args;
        }}
      />
    );

    const placeholder = component.locator("#dynamic_tasks_DF_TASK_PLACEHOLDER");
    await expect(placeholder).toContainText("3 of 3 tasks succeeded");
    await placeholder.click();
    expect(clickedWith).toEqual({ ref: "first_task" });
  });

  test("Dynamic Fork - one task failed", async ({ mount }) => {
    let clickedWith: unknown;
    const component = await mount(
      <WorkflowGraphTestBed
        rawData={oneFailedData}
        executionMode={true}
        onClickCapture={(args) => {
          clickedWith = args;
        }}
      />
    );

    const placeholder = component.locator("#dynamic_tasks_DF_TASK_PLACEHOLDER");
    await expect(placeholder).toContainText("2 of 3 tasks succeeded");
    await expect(placeholder).toHaveClass(/status_FAILED/);
    await placeholder.click();
    expect(clickedWith).toEqual({ ref: "first_task" });
  });

  test("Dynamic Fork - externalized input", async ({ mount }) => {
    let clickedWith: unknown;
    const component = await mount(
      <WorkflowGraphTestBed
        rawData={externalizedInputData}
        executionMode={true}
        onClickCapture={(args) => {
          clickedWith = args;
        }}
      />
    );

    const placeholder = component.locator("#dynamic_tasks_DF_TASK_PLACEHOLDER");
    await expect(placeholder).toContainText("3 of 3 tasks succeeded");
    await placeholder.click();
    expect(clickedWith).toEqual({ ref: "first_task" });
  });

  test("Dynamic Fork - not executed", async ({ mount }) => {
    const component = await mount(
      <WorkflowGraphTestBed rawData={notExecutedData} executionMode={true} />
    );

    const placeholder = component.locator(
      "#dynamic_tasks_DF_EMPTY_PLACEHOLDER"
    );
    await expect(placeholder).toHaveClass(/dimmed/);
    await expect(placeholder).toContainText("Dynamically spawned tasks");
  });

  test("Dynamic Fork - none spawned", async ({ mount }) => {
    let clickCalled = false;
    const component = await mount(
      <WorkflowGraphTestBed
        rawData={noneSpawnedData}
        executionMode={true}
        onClickCapture={() => {
          clickCalled = true;
        }}
      />
    );

    const placeholder = component.locator(
      "#dynamic_tasks_DF_EMPTY_PLACEHOLDER"
    );
    await expect(placeholder).toContainText("No tasks spawned");
    await placeholder.click();
    expect(clickCalled).toBe(false);
  });

  // Note: The addition of task 'inline_task_outside' tests prefix-based loop content detection.
  // Will succeed only when filtering via 'prefix + "__"'.
  test("Do_while containing switch (definition)", async ({ mount }) => {
    const component = await mount(
      <WorkflowGraphTestBed
        workflowDefinition={(doWhileSwitchData as any).workflowDefinition}
        executionMode={false}
      />
    );

    await expect(
      component.locator(".edgePaths .edgePath.reverse")
    ).toBeVisible();
    await expect(component.locator(".edgePaths .edgePath")).toHaveCount(11);
    await expect(component.locator(".edgeLabels")).toContainText("LOOP");
  });

  test("Do_while containing switch (execution)", async ({ mount }) => {
    let clickedWith: unknown;
    const component = await mount(
      <WorkflowGraphTestBed
        rawData={doWhileSwitchData}
        executionMode={true}
        onClickCapture={(args) => {
          clickedWith = args;
        }}
      />
    );

    const placeholder = component.locator("#LoopTask_DF_TASK_PLACEHOLDER");
    await expect(placeholder).toContainText("2 of 2 tasks succeeded");
    await placeholder.click();
    expect(clickedWith).toEqual({ ref: "inline_task__1" });

    await expect(component.locator(".edgePaths .edgePath")).toHaveCount(6);
    await expect(
      component.locator(".edgePaths .edgePath.reverse")
    ).toBeVisible();
    await expect(component.locator(".edgeLabels")).toContainText("LOOP");
  });
});
