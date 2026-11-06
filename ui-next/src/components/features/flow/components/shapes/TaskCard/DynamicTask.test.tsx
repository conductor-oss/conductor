import { render, screen } from "@testing-library/react";
import { DynamicTaskDef, TaskStatus, TaskType } from "types";
import { DynamicTask } from "./DynamicTask";

const baseTask = {
  name: "dynamic_subworkflow",
  taskReferenceName: "dynamic_subworkflow_ref",
  type: TaskType.DYNAMIC,
  dynamicTaskNameParam: "taskToExecute",
  inputParameters: { taskToExecute: "SUB_WORKFLOW" },
} as DynamicTaskDef;

// taskToNode spreads the task's executionData (status, outputData, …) into the
// node data, so an executed node carries outputData at the top level.
const renderCard = (
  task: DynamicTaskDef,
  outputData?: Record<string, unknown>,
) =>
  render(
    <DynamicTask
      nodeData={{ crumbs: [], task, status: TaskStatus.COMPLETED, outputData }}
    />,
  );

describe("DYNAMIC task diagram card", () => {
  it("links to the sub workflow execution", () => {
    renderCard(baseTask, { subWorkflowId: "child-123" });

    expect(screen.getByRole("link", { name: "child-123" })).toHaveAttribute(
      "href",
      "/execution/child-123",
    );
  });

  it("links even when taskToExecute is an expression", () => {
    renderCard(
      {
        ...baseTask,
        inputParameters: { taskToExecute: "${workflow.input.taskType}" },
      },
      { subWorkflowId: "child-456" },
    );

    expect(screen.getByRole("link", { name: "child-456" })).toBeInTheDocument();
  });

  it("renders nothing in the definition view", () => {
    const { container } = renderCard(baseTask);

    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing when the task produced no sub workflow", () => {
    const { container } = renderCard(
      {
        ...baseTask,
        inputParameters: { taskToExecute: "my_simple_task" },
      },
      { result: "ok" },
    );

    expect(container).toBeEmptyDOMElement();
  });
});
