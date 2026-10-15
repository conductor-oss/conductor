import "@testing-library/jest-dom";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { describe, expect, it } from "vitest";
import { ExecutionTask } from "types";
import TaskSummary from "./TaskSummary";

const taskResult = {
  taskType: "SIMPLE",
  status: "SCHEDULED",
  domain: "canary",
  workflowTask: {
    type: "SIMPLE",
    name: "validate",
    taskReferenceName: "validate_ref",
  },
} as unknown as ExecutionTask;

describe("TaskSummary", () => {
  it("labels the task's domain as a task domain", () => {
    render(
      <MemoryRouter>
        <TaskSummary taskResult={taskResult} />
      </MemoryRouter>,
    );

    expect(screen.getByText("Task domain")).toBeInTheDocument();
    expect(screen.queryByText("Domain")).not.toBeInTheDocument();
    expect(screen.getByText("canary")).toBeInTheDocument();
  });
});
