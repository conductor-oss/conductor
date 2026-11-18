import { describe, expect, it } from "vitest";
import { TaskStatus, TaskType } from "types";
import {
  codeLoopStatus,
  codeLoopsAsIterations,
  codeLoopsInDefinition,
} from "./codeLoops";

const step = (
  ref: string,
  site: string,
  loops: string[] = [],
  extra: object = {},
) => ({
  name: ref.replace(/_\d+$/, ""),
  taskReferenceName: ref,
  type: "SIMPLE",
  metadata: { loops, site },
  ...extra,
});

const ran = (
  def: any,
  seq: number,
  output: object = { result: def.taskReferenceName },
) => ({
  taskId: `id-${def.taskReferenceName}`,
  referenceTaskName: def.taskReferenceName,
  taskType: def.type,
  status: TaskStatus.COMPLETED,
  seq,
  startTime: 1000 + seq,
  endTime: 1001 + seq,
  outputData: output,
  workflowTask: def,
});

const execution = (defs: any[], tasks: any[]) => ({
  workflowDefinition: { tasks: defs },
  tasks,
});

const refs = (list: any[]) => list.map((t) => t.taskReferenceName);

describe("codeLoopsAsIterations", () => {
  it("shows a call made on each pass as one step with an iteration per run", () => {
    const defs = ["note", "note_2", "note_3", "note_4"].map((r) =>
      step(r, "Hello.hello:63", ["l"]),
    );
    const shown: any = codeLoopsAsIterations(
      execution(
        defs,
        defs.map((d, i) => ran(d, i + 1)),
      ),
    );

    const [loop] = shown.workflowDefinition.tasks;
    expect(loop.type).toBe(TaskType.DO_WHILE);
    expect(refs(loop.loopOver)).toEqual(["note"]);

    const [doWhile, ...runs] = shown.tasks;
    expect(doWhile.taskType).toBe(TaskType.DO_WHILE);
    expect(doWhile.referenceTaskName).toBe("l");
    expect(doWhile.iteration).toBe(4);
    expect(doWhile.status).toBe(TaskStatus.COMPLETED);
    expect(doWhile.outputData).toEqual({
      iteration: 4,
      1: { note: { result: "note" } },
      2: { note: { result: "note_2" } },
      3: { note: { result: "note_3" } },
      4: { note: { result: "note_4" } },
    });
    expect(runs.map((t: any) => t.referenceTaskName)).toEqual([
      "note",
      "note_2",
      "note_3",
      "note_4",
    ]);
    expect(runs.map((t: any) => t.workflowTask.taskReferenceName)).toEqual([
      "note",
      "note",
      "note",
      "note",
    ]);
    expect(runs.map((t: any) => t.iteration)).toEqual([1, 2, 3, 4]);
    expect(runs.every((t: any) => t.loopOverTask)).toBe(true);
  });

  it("starts a pass when the code comes back to a call the pass already made", () => {
    // for (...) { a(); if (c) b(); } ran a, b | a | a, b
    const defs = [
      step("a", "W.run:10", ["l"]),
      step("b", "W.run:11", ["l"]),
      step("a_2", "W.run:10", ["l"]),
      step("a_3", "W.run:10", ["l"]),
      step("b_2", "W.run:11", ["l"]),
    ];
    const shown: any = codeLoopsAsIterations(
      execution(
        defs,
        defs.map((d, i) => ran(d, i + 1)),
      ),
    );
    expect(refs(shown.workflowDefinition.tasks[0].loopOver)).toEqual([
      "a",
      "b",
    ]);
    const iterations = Object.fromEntries(
      shown.tasks
        .filter((t: any) => t.loopOverTask)
        .map((t: any) => [t.referenceTaskName, t.iteration]),
    );
    expect(iterations).toEqual({ a: 1, b: 1, a_2: 2, a_3: 3, b_2: 3 });
    expect(shown.tasks[0].iteration).toBe(3);
    expect(Object.keys(shown.tasks[0].outputData[2])).toEqual(["a"]);
  });

  it("merges the arms a switch took on different passes, preferring a step that ran over its preview", () => {
    const sw = (ref: string, taken: any[], other: any[]) =>
      step(ref, "W.run:20", ["l"], {
        type: "SWITCH",
        name: "if_c",
        decisionCases: { true: taken },
        defaultCase: other,
      });
    const defs: any[] = [
      sw(
        "if_c",
        [step("a", "W.run:21", ["l"])],
        [
          step("b_if_false", "W.run:23", ["l"], {
            name: "b",
            metadata: { loops: ["l"], site: "W.run:23", preview: true },
          }),
        ],
      ),
      sw(
        "if_c_2",
        [
          step("a_if_true", "W.run:21", ["l"], {
            name: "a",
            metadata: { loops: ["l"], site: "W.run:21", preview: true },
          }),
        ],
        [step("b", "W.run:23", ["l"])],
      ),
    ];
    const tasks = [
      ran(defs[0], 1),
      ran(defs[0].decisionCases.true[0], 2),
      ran(defs[1], 3),
      ran(defs[1].defaultCase[0], 4),
    ];
    const shown: any = codeLoopsAsIterations(execution(defs, tasks));
    const [body] = shown.workflowDefinition.tasks[0].loopOver;
    expect(body.taskReferenceName).toBe("if_c");
    expect(refs(body.decisionCases.true)).toEqual(["a"]);
    expect(refs(body.defaultCase)).toEqual(["b"]);
    const byRef = Object.fromEntries(
      shown.tasks.map((t: any) => [t.referenceTaskName, t]),
    );
    expect(byRef.if_c_2.workflowTask.taskReferenceName).toBe("if_c");
    expect(byRef.if_c_2.iteration).toBe(2);
    expect(byRef.b.iteration).toBe(2);
    expect(byRef.l.iteration).toBe(2);
  });

  it("knows a switch is the same place on every pass, though each is named after its reference", () => {
    const sw = (ref: string, inside: string) =>
      step(ref, "W.run:40", ["l"], {
        type: "SWITCH",
        name: ref,
        decisionCases: { true: [step(inside, "W.run:41", ["l"])] },
      });
    const defs: any[] = [sw("if_d", "a"), sw("if_d_2", "a_2")];
    const tasks = [
      ran(defs[0], 1),
      ran(defs[0].decisionCases.true[0], 2),
      ran(defs[1], 3),
      ran(defs[1].decisionCases.true[0], 4),
    ];
    const shown: any = codeLoopsAsIterations(execution(defs, tasks));
    expect(refs(shown.workflowDefinition.tasks[0].loopOver)).toEqual(["if_d"]);
    expect(shown.tasks[0].iteration).toBe(2);
  });

  it("knows the preview of a switch and the switch itself are the same place", () => {
    // pass 1 went past the inner if (previewed in the outer default); pass 2 took it
    const inner = (ref: string, preview: boolean) =>
      step(ref, "W.run:51", preview ? [] : ["l"], {
        type: "SWITCH",
        name: ref,
        decisionCases: { true: [] },
        ...(preview ? { metadata: { site: "W.run:51", preview: true } } : {}),
      });
    const outer = (ref: string, inside: any) =>
      step(ref, "W.run:50", ["l"], {
        type: "SWITCH",
        name: ref,
        decisionCases: { true: [] },
        defaultCase: [inside],
      });
    const defs: any[] = [
      outer("if_o", inner("if_i_if_false", true)),
      outer("if_o_2", inner("if_i", false)),
    ];
    const shown: any = codeLoopsAsIterations(
      execution(defs, [
        ran(defs[0], 1),
        ran(defs[1], 2),
        ran(defs[1].defaultCase[0], 3),
      ]),
    );
    const [body] = shown.workflowDefinition.tasks[0].loopOver;
    expect(refs(body.defaultCase)).toEqual(["if_i"]);
  });

  it("takes a step retried after a failure as its latest attempt", () => {
    const defs = [step("a", "W.run:1", ["l"])];
    const failed = { ...ran(defs[0], 1), status: TaskStatus.FAILED };
    const shown: any = codeLoopsAsIterations(
      execution(defs, [failed, ran(defs[0], 2)]),
    );
    expect(shown.tasks[0].status).toBe(TaskStatus.COMPLETED);
  });

  it("shows a loop inside a loop as a loop in its body", () => {
    // for (o) { for (i) { x(); } }: x, x | x
    const x = (ref: string) => step(ref, "W.run:31", ["o", "i"]);
    const defs = [
      x("x"),
      x("x_2"),
      step("y", "W.run:33", ["o"]),
      x("x_3"),
      step("y_2", "W.run:33", ["o"]),
    ];
    const shown: any = codeLoopsAsIterations(
      execution(
        defs,
        defs.map((d, i) => ran(d, i + 1)),
      ),
    );
    const [outer] = shown.workflowDefinition.tasks;
    expect(outer.taskReferenceName).toBe("o");
    expect(refs(outer.loopOver)).toEqual(["i", "y"]);
    expect(refs(outer.loopOver[0].loopOver)).toEqual(["x"]);
    expect(shown.tasks[0].referenceTaskName).toBe("o");
    expect(shown.tasks[0].iteration).toBe(2);
    const inner = shown.tasks.filter((t: any) => t.taskDefName === "i");
    expect(
      inner.map((t: any) => [t.referenceTaskName, t.outputData.iteration]),
    ).toEqual([
      ["i__1", 2],
      ["i__2", 1],
    ]);
  });

  it("gives a loop that runs again later its own node", () => {
    const defs = [
      step("a", "W.run:1", ["l"]),
      step("b", "W.run:5"),
      step("a_2", "W.run:1", ["l"]),
    ];
    const shown: any = codeLoopsAsIterations(
      execution(
        defs,
        defs.map((d, i) => ran(d, i + 1)),
      ),
    );
    expect(refs(shown.workflowDefinition.tasks)).toEqual(["l", "b", "l_2"]);
    expect(refs(shown.workflowDefinition.tasks[2].loopOver)).toEqual(["a_2"]);
  });

  it("leaves an execution without Java loops as it is, and is applied once", () => {
    const plain = execution([step("a", "W.run:1")], []);
    expect(codeLoopsAsIterations(plain)).toBe(plain);
    const looped = execution([step("a", "W.run:1", ["l"])], []);
    const once = codeLoopsAsIterations(looped);
    expect(codeLoopsAsIterations(once)).toBe(once);
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

describe("codeLoopsInDefinition", () => {
  it("draws each Java loop of a registered definition as one loop node", () => {
    // while, do-while (its condition calls a task) and for (;;), each call site once
    const definition = {
      name: "loop_shapes",
      tasks: [
        step("loop_step", "LoopShapes.run:60", ["L59"]),
        step("loop_step_2", "LoopShapes.run:68", ["L67"]),
        step("loop_ready", "LoopShapes.run:69", ["L67"]),
        step("loop_step_3", "LoopShapes.run:74", ["L74"]),
        step("after", "LoopShapes.run:80"),
      ],
    };

    const shown: any = codeLoopsInDefinition(definition);

    expect(shown.tasks.map((t: any) => [t.taskReferenceName, t.type])).toEqual([
      ["L59", TaskType.DO_WHILE],
      ["L67", TaskType.DO_WHILE],
      ["L74", TaskType.DO_WHILE],
      ["after", "SIMPLE"],
    ]);
    expect(refs(shown.tasks[0].loopOver)).toEqual(["loop_step"]);
    expect(refs(shown.tasks[1].loopOver)).toEqual([
      "loop_step_2",
      "loop_ready",
    ]);
    expect(refs(shown.tasks[2].loopOver)).toEqual(["loop_step_3"]);
    expect(definition.tasks).toHaveLength(5); // the definition itself is unchanged
  });

  it("nests an inner loop in its outer one, and leaves an untagged definition as it is", () => {
    const shown: any = codeLoopsInDefinition({
      tasks: [
        step("probe", "Fleet.run:18", ["outer", "inner"]),
        step("report", "Fleet.run:56", ["outer"]),
      ],
    });
    expect(refs(shown.tasks)).toEqual(["outer"]);
    expect(shown.tasks[0].loopOver.map((t: any) => t.type)).toEqual([
      TaskType.DO_WHILE,
      "SIMPLE",
    ]);
    expect(refs(shown.tasks[0].loopOver[0].loopOver)).toEqual(["probe"]);

    const plain = { tasks: [step("a", "X.run:1")] };
    expect(codeLoopsInDefinition(plain)).toBe(plain);
  });
});
