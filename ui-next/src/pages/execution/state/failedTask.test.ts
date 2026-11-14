import { describe, expect, it } from "vitest";
import { ExecutionTask } from "types/Execution";
import { failedTaskOf } from "./failedTask";

const task = (taskId: string, status: string, reason?: string) =>
  ({
    taskId,
    referenceTaskName: taskId,
    status,
    reasonForIncompletion: reason,
  }) as unknown as ExecutionTask;

// a retried attempt failed first, the workflow handled that, then step_7 ended the run
const tasks = [
  task("get_es_1", "FAILED", "connection refused"),
  task("step_7", "FAILED_WITH_TERMINAL_ERROR", "injected terminal failure"),
  task("throw", "FAILED", "NumberFormatException"),
];

describe("failedTaskOf", () => {
  it("is the task the server names as the cause", () => {
    expect(failedTaskOf(tasks, "step_7")?.referenceTaskName).toBe("step_7");
    expect(failedTaskOf(tasks, "throw")?.referenceTaskName).toBe("throw");
  });

  it("is the first failed task with a reason when the server names none", () => {
    expect(failedTaskOf(tasks, undefined)?.referenceTaskName).toBe("get_es_1");
  });

  it("falls back when the named task is not in the list", () => {
    expect(failedTaskOf(tasks, "gone")?.referenceTaskName).toBe("get_es_1");
  });

  it("is nothing without tasks", () => {
    expect(failedTaskOf(undefined, "step_7")).toBeUndefined();
  });
});
