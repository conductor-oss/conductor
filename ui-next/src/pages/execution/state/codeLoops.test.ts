import { describe, expect, it } from "vitest";
import { TaskStatus, TaskType } from "types";
import { codeLoopStatus, groupCodeLoops } from "./codeLoops";

const step = (ref: string, loops: string[] = [], extra: object = {}) => ({
  name: ref,
  taskReferenceName: ref,
  type: "SIMPLE",
  metadata: { loops },
  ...extra,
});

describe("groupCodeLoops", () => {
  it("draws consecutive steps of a loop as one loop node", () => {
    const grouped: any[] = groupCodeLoops([
      step("a"),
      step("s1", ["l"]),
      step("s2", ["l"]),
      step("b"),
    ] as any[]);
    expect(grouped.map((t) => t.taskReferenceName)).toEqual(["a", "l", "b"]);
    expect(grouped[1].type).toBe(TaskType.DO_WHILE);
    expect(grouped[1].loopOver.map((t: any) => t.taskReferenceName)).toEqual([
      "s1",
      "s2",
    ]);
  });

  it("nests loops, and leaves what is inside a switch in the loop", () => {
    const sw = step("sw", ["o", "i"], {
      type: "SWITCH",
      decisionCases: { x: [step("in", ["o", "i"])] },
      defaultCase: [],
    });
    const [outer]: any[] = groupCodeLoops([sw] as any[]);
    const [inner] = outer.loopOver;
    expect(inner.taskReferenceName).toBe("i");
    expect(inner.loopOver[0].taskReferenceName).toBe("sw");
    expect(
      inner.loopOver[0].decisionCases.x.map((t: any) => t.taskReferenceName),
    ).toEqual(["in"]);
  });

  it("finds a loop started inside a case or a branch", () => {
    const [sw]: any[] = groupCodeLoops([
      {
        ...step("sw"),
        decisionCases: { x: [step("t", ["l"])] },
        forkTasks: [[step("u", ["m"])]],
      },
    ] as any[]);
    expect(sw.decisionCases.x[0].taskReferenceName).toBe("l");
    expect(sw.forkTasks[0][0].taskReferenceName).toBe("m");
  });

  it("gives a loop that runs again later its own reference", () => {
    const grouped: any[] = groupCodeLoops([
      step("a", ["l"]),
      step("b"),
      step("c", ["l"]),
    ] as any[]);
    expect(grouped.map((t) => t.taskReferenceName)).toEqual(["l", "b", "l_2"]);
  });

  it("gives a loop the status of its steps", () => {
    const of = (s: TaskStatus) => ({ executionData: { status: s } });
    expect(
      codeLoopStatus([of(TaskStatus.COMPLETED), of(TaskStatus.IN_PROGRESS)]),
    ).toBe(TaskStatus.IN_PROGRESS);
    expect(
      codeLoopStatus([of(TaskStatus.COMPLETED), of(TaskStatus.COMPLETED)]),
    ).toBe(TaskStatus.COMPLETED);
    expect(codeLoopStatus([of(TaskStatus.PENDING)])).toBe(TaskStatus.PENDING);
  });
});
