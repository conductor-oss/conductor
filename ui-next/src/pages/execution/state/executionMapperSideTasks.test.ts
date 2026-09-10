import { describe, expect, it } from "vitest";
import { TaskStatus, TaskType } from "types";
import { executionToWorkflowDef } from "./executionMapper";

/**
 * The end of the pipeline that matters for the diagram: an execution carrying a task the definition
 * never declared comes out with that task attached to the node it was run for, and the definition's
 * own tasks are untouched.
 *
 * The shape here is the one the console actually receives from `GET /workflow/{id}?summarize=true`.
 */
describe("side tasks on the execution definition", () => {
  const execution = {
    workflowDefinition: {
      name: "guarded",
      version: 1,
      tasks: [
        {
          name: "llm",
          taskReferenceName: "llm",
          type: TaskType.SIMPLE,
          inputParameters: {},
        },
        {
          name: "notify",
          taskReferenceName: "notify",
          type: TaskType.SIMPLE,
          inputParameters: {},
        },
      ],
    },
    tasks: [
      {
        taskId: "task-1",
        referenceTaskName: "llm",
        taskType: TaskType.SIMPLE,
        workflowTask: { name: "llm", taskReferenceName: "llm", type: "SIMPLE" },
        status: TaskStatus.COMPLETED,
        executed: true,
        workflowType: "guarded",
        loopOverTask: false,
      },
      {
        taskId: "task-2",
        referenceTaskName: "_guardrail_grb_abc_0",
        parentTaskReferenceName: "llm",
        taskType: TaskType.SIMPLE,
        workflowTask: {
          name: "examples_guardrail_worker",
          taskReferenceName: "_guardrail_grb_abc_0",
          type: "SIMPLE",
        },
        status: TaskStatus.COMPLETED,
        executed: true,
        workflowType: "guarded",
        loopOverTask: false,
      },
    ],
  } as any;

  it("attaches the detached task to the task it was run for", () => {
    const [definition] = executionToWorkflowDef(execution);

    const llm = definition.tasks.find(
      (t: any) => t.taskReferenceName === "llm",
    ) as any;
    expect(llm.executionData.sideTasks).toHaveLength(1);
    expect(llm.executionData.sideTasks[0].taskId).toBe("task-2");
    expect(llm.executionData.status).toBe(TaskStatus.COMPLETED);
  });

  it("leaves every other task without side tasks", () => {
    const [definition] = executionToWorkflowDef(execution);

    const notify = definition.tasks.find(
      (t: any) => t.taskReferenceName === "notify",
    ) as any;
    expect(notify.executionData.sideTasks).toBeUndefined();
  });

  it("never turns a detached task into a node of its own", () => {
    const [definition] = executionToWorkflowDef(execution);

    expect(definition.tasks.map((t: any) => t.taskReferenceName)).toEqual([
      "llm",
      "notify",
    ]);
  });
});
