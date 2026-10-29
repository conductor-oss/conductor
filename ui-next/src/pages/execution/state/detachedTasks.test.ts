import { describe, expect, it } from "vitest";
import { TaskType } from "types";
import {
  detachedTasksByParent,
  isDetached,
  isSideTaskOf,
  declaredReferenceNames,
} from "./detachedTasks";

/**
 * The console and the server have to agree on what "outside the DAG" means, or the diagram will
 * show tasks the workflow was actually waiting on, or hide ones it was not. These cases mirror the
 * server's DetachedTasksTest one for one.
 */
describe("detached tasks", () => {
  const definition = {
    tasks: [
      { taskReferenceName: "llm", name: "llm", type: TaskType.SIMPLE },
      {
        taskReferenceName: "fork",
        name: "fork",
        type: TaskType.FORK_JOIN,
        forkTasks: [
          [
            {
              taskReferenceName: "branch",
              name: "branch",
              type: TaskType.SIMPLE,
            },
          ],
        ],
      },
      {
        taskReferenceName: "loop",
        name: "loop",
        type: TaskType.DO_WHILE,
        loopOver: [
          { taskReferenceName: "inner", name: "inner", type: TaskType.SIMPLE },
        ],
      },
    ],
  } as any;

  const declared = declaredReferenceNames(definition);

  const task = (referenceTaskName: string, parent?: string) =>
    ({
      referenceTaskName,
      parentTaskReferenceName: parent,
      workflowTask: {
        name: referenceTaskName,
        taskReferenceName: referenceTaskName,
        type: "SIMPLE",
      },
    }) as any;

  it("counts every reference the definition declares, including nested ones", () => {
    expect([...declared].sort()).toEqual([
      "branch",
      "fork",
      "inner",
      "llm",
      "loop",
    ]);
  });

  it("does not call a declared task detached", () => {
    expect(isDetached(declared, task("llm"))).toBe(false);
    expect(isDetached(declared, task("branch"))).toBe(false);
    expect(isDetached(declared, task("inner"))).toBe(false);
  });

  it("calls a task the definition never declared detached", () => {
    expect(isDetached(declared, task("_guardrail_grb_1_0"))).toBe(true);
  });

  it("does not call a loop iteration detached, despite its suffix", () => {
    expect(isDetached(declared, task("inner__1"))).toBe(false);
  });

  it("calls nothing detached when there is no definition to compare against", () => {
    expect(
      isDetached(declaredReferenceNames(undefined), task("anything")),
    ).toBe(false);
    expect(detachedTasksByParent([task("anything", "llm")], undefined)).toEqual(
      {},
    );
  });

  it("groups detached tasks under the task they were run for", () => {
    const grouped = detachedTasksByParent(
      [
        task("llm"),
        task("_guardrail_pii_0", "llm"),
        task("_guardrail_toxicity_0", "llm"),
        task("_guardrail_other_0", "loop"),
      ],
      definition,
    );

    expect(Object.keys(grouped).sort()).toEqual(["llm", "loop"]);
    expect(grouped.llm.map((t: any) => t.referenceTaskName)).toEqual([
      "_guardrail_pii_0",
      "_guardrail_toxicity_0",
    ]);
  });

  it("leaves a static fork's branch alone, because the definition declares it", () => {
    const grouped = detachedTasksByParent([task("branch", "fork")], definition);

    expect(grouped).toEqual({});
  });

  /**
   * The case the definition check alone does NOT catch, and the reason the parent's type matters.
   * A FORK_JOIN_DYNAMIC has no static children, so a branch's reference is never in the definition,
   * and the server stamps the fork's reference onto every branch as its parent — exactly the shape
   * of a guardrail detector. Branches are the workflow's own work and belong in the graph, drawn by
   * the diagram's dynamic-fork handling, so only the parent's type separates the two.
   */
  it("leaves a dynamic fork's branches alone, though they match on both other counts", () => {
    const withDynamicFork = {
      tasks: [
        {
          taskReferenceName: "dyn_fork",
          name: "dyn_fork",
          type: TaskType.FORK_JOIN_DYNAMIC,
        },
        { taskReferenceName: "join", name: "join", type: TaskType.JOIN },
      ],
    } as any;
    const branch = task("_dyn_fork_0", "dyn_fork");

    expect(isDetached(declaredReferenceNames(withDynamicFork), branch)).toBe(
      true,
    );
    expect(detachedTasksByParent([branch], withDynamicFork)).toEqual({});
  });

  it("ignores a detached task whose parent the diagram does not draw", () => {
    const grouped = detachedTasksByParent(
      [task("_guardrail_pii_0", "nowhere")],
      definition,
    );

    expect(grouped).toEqual({});
  });
});

/**
 * The predicate the right panel uses to decide whether "Re-Run from Task" applies. Same rule as the
 * grouping above, asked of one task, so the button and the diagram can never disagree about what a
 * side task is.
 */
describe("isSideTaskOf", () => {
  const definition = {
    tasks: [
      { taskReferenceName: "llm", name: "llm", type: TaskType.SIMPLE },
      {
        taskReferenceName: "dyn_fork",
        name: "dyn_fork",
        type: TaskType.FORK_JOIN_DYNAMIC,
      },
    ],
  } as any;

  const executionTask = (referenceTaskName: string, parent?: string) =>
    ({ referenceTaskName, parentTaskReferenceName: parent }) as any;

  it("is true for a task attached to a step of the workflow", () => {
    expect(
      isSideTaskOf(executionTask("_guardrail_pii_0", "llm"), definition),
    ).toBe(true);
  });

  it("is false for a dynamic fork's branch", () => {
    expect(
      isSideTaskOf(executionTask("_dyn_fork_0", "dyn_fork"), definition),
    ).toBe(false);
  });

  it("is false for a step of the workflow, and for nothing selected", () => {
    expect(isSideTaskOf(executionTask("llm"), definition)).toBe(false);
    expect(isSideTaskOf(undefined, definition)).toBe(false);
    expect(
      isSideTaskOf(executionTask("_guardrail_pii_0", "llm"), undefined),
    ).toBe(false);
  });
});
