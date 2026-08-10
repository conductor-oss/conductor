import React from "react";
import { test, expect } from "@playwright/experimental-ct-react";

// @ts-ignore - JS module without type declarations
import WorkflowDAG from "../../components/diagram/WorkflowDAG";
// @ts-ignore - JS module without type declarations
import TimelineComponent from "./Timeline";

import doWhileSwitchData from "../../../e2e/fixtures/doWhile/doWhileSwitch.json";
import dynamicForkSuccessData from "../../../e2e/fixtures/dynamicFork/success.json";

// Wrapper instantiates WorkflowDAG in the browser context (avoids serialization issues).
// Accepts an optional tasks override; defaults to rawData.tasks.
function TimelineTestBed({
  rawData,
  tasks,
  onClickCapture,
  selectedTask = null,
}: {
  rawData: { tasks: object[] } & object;
  tasks?: object[];
  onClickCapture?: (...args: unknown[]) => void;
  selectedTask?: unknown;
}) {
  const dag = new WorkflowDAG(rawData);
  return (
    <TimelineComponent
      dag={dag}
      tasks={tasks ?? rawData.tasks}
      onClick={onClickCapture ?? (() => {})}
      selectedTask={selectedTask}
    />
  );
}

test.describe("<Timeline>", () => {
  test("Do_while containing switch - renders without crashing", async ({
    mount,
  }) => {
    const component = await mount(
      <TimelineTestBed rawData={doWhileSwitchData} />
    );

    await expect(component.locator(".timeline-container")).toBeVisible();
    await expect(component.locator(".vis-timeline")).toBeVisible();
    await expect(component.locator(".vis-item").first()).toBeVisible();
  });

  test("Do_while containing switch - handles tasks with no dfParent", async ({
    mount,
  }) => {
    const component = await mount(
      <TimelineTestBed rawData={doWhileSwitchData} />
    );

    await expect(component.locator(".timeline-container")).toBeVisible();
    await expect(component.locator(".vis-labelset")).toBeVisible();
  });

  test("Dynamic Fork - renders timeline correctly", async ({ mount }) => {
    const component = await mount(
      <TimelineTestBed rawData={dynamicForkSuccessData} />
    );

    await expect(component.locator(".timeline-container")).toBeVisible();
    await expect(component.locator(".vis-timeline")).toBeVisible();
    await expect(component.locator(".vis-item").first()).toBeVisible();
  });

  test("Timeline handles tasks with start/end times correctly", async ({
    mount,
  }) => {
    const filteredTasks = (doWhileSwitchData as any).tasks.filter(
      (t: any) => t.startTime > 0 || t.endTime > 0
    );
    const component = await mount(
      <TimelineTestBed rawData={doWhileSwitchData} tasks={filteredTasks} />
    );

    await expect(component.locator(".vis-item")).toHaveCount(
      filteredTasks.length
    );
  });

  test("Timeline click handler is triggered", async ({ mount }) => {
    let clickCalled = false;
    const component = await mount(
      <TimelineTestBed
        rawData={doWhileSwitchData}
        onClickCapture={() => {
          clickCalled = true;
        }}
      />
    );

    await component.locator(".vis-item").first().click();
    expect(clickCalled).toBe(true);
  });
});
