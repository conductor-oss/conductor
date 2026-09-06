import { describe, expect, it } from "vitest";
import { TaskType } from "types";
import {
  detachedTasksByParent,
  isDetached,
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

  it("leaves a dynamic fork's children alone, even though they name a parent", () => {
    // They are real nodes of the graph: the definition declares them once expanded, and the
    // workflow's join waits on them. Only the definition check keeps them out.
    const grouped = detachedTasksByParent([task("branch", "fork")], definition);

    expect(grouped).toEqual({});
  });

  it("ignores a detached task whose parent the diagram does not draw", () => {
    const grouped = detachedTasksByParent(
      [task("_guardrail_pii_0", "nowhere")],
      definition,
    );

    expect(grouped).toEqual({});
  });
});
