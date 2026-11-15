import { describe, expect, it, vi } from "vitest";
import { withCodePreview, withWhatIsAhead } from "./services";

const registered = {
  name: "loop_shapes",
  version: 1,
  tasks: [{ taskReferenceName: "loop_step", type: "SIMPLE" }],
};

const execution = (tasks: any[], status = "RUNNING"): any => ({
  status,
  workflowName: "loop_shapes",
  workflowVersion: 1,
  workflowDefinition: {
    name: "loop_shapes",
    version: 1,
    metadata: { native: { codeType: "loop_shapes.v1.code" } },
    tasks,
  },
});

/** An entry at a line of the code. */
const at = (ref: string, line: number, more: any = {}): any => ({
  name: ref,
  taskReferenceName: ref,
  type: "SIMPLE",
  ...more,
  metadata: { site: `W.run:${line}`, ...more.metadata },
});

describe("withCodePreview", () => {
  it("shows the registered definition while the code has not run", async () => {
    const fetchDefinition = vi.fn().mockResolvedValue(registered);
    const shown = await withCodePreview(execution([]), fetchDefinition);
    expect(fetchDefinition).toHaveBeenCalledWith("loop_shapes", 1);
    expect(shown.codePreview).toBe(true);
    expect(shown.workflowDefinition.tasks).toEqual(registered.tasks);
    expect(shown.workflowDefinition.metadata.native).toBeDefined();
  });

  it("keeps the execution's own definition when nothing is left to preview, or when it ended", async () => {
    const fetchDefinition = vi.fn().mockResolvedValue(registered);
    const started = execution([at("loop_step", 1)]);
    expect(await withCodePreview(started, fetchDefinition)).toBe(started);
    const ended = execution([], "TERMINATED");
    expect(await withCodePreview(ended, vi.fn())).toBe(ended);
  });

  it("previews what the code has yet to do after the step it is at", async () => {
    const fetchDefinition = vi.fn().mockResolvedValue({
      tasks: [at("a", 1), at("b", 2), at("c", 3)],
    });
    const shown = await withCodePreview(
      execution([at("a", 1), at("b", 2)]),
      fetchDefinition,
    );
    expect(shown.codeAhead).toBe(true);
    const tasks = shown.workflowDefinition.tasks;
    expect(tasks.map((t: any) => t.taskReferenceName)).toEqual(["a", "b", "c"]);
    expect(tasks[2].metadata.preview).toBe(true);
    expect(tasks[1].metadata.preview).toBeUndefined();
  });

  it("leaves a workflow that is not native, or a definition it cannot fetch, as it is", async () => {
    const plain = { status: "RUNNING", workflowDefinition: { tasks: [] } };
    expect(await withCodePreview(plain, vi.fn())).toBe(plain);
    const pending = execution([]);
    const failing = vi.fn().mockRejectedValue(new Error("404"));
    expect(await withCodePreview(pending, failing)).toBe(pending);
  });
});

describe("withWhatIsAhead", () => {
  it("previews the rest of the arm the code is in, then what follows the switch", () => {
    const sw = (cases: any, defaultCase: any[]) =>
      at("if_x", 2, { type: "SWITCH", decisionCases: cases, defaultCase });
    const registered = [
      at("a", 1),
      sw({ true: [at("b", 3), at("c", 4)] }, [at("d", 6)]),
      at("e", 8),
    ];
    // the code took the true arm and is at b; the other arm is the SDK's own preview
    const own = [
      at("a", 1),
      sw({ true: [at("b", 3)] }, [
        at("d_if_false", 6, { metadata: { preview: true } }),
      ]),
    ];
    const shown = withWhatIsAhead(own, registered);
    expect(shown.map((t) => t.taskReferenceName)).toEqual(["a", "if_x", "e"]);
    expect(
      shown[1].decisionCases.true.map((t: any) => t.taskReferenceName),
    ).toEqual(["b", "c"]);
    expect(shown[1].decisionCases.true[1].metadata.preview).toBe(true);
    expect(shown[1].defaultCase.map((t: any) => t.taskReferenceName)).toEqual([
      "d_if_false",
    ]);
  });

  it("previews what each branch of the fork the code waits for has yet to do", () => {
    const fork = (branches: any[][]) =>
      at("fork", 2, { type: "FORK_JOIN", forkTasks: branches });
    const join = at("fork_join", 2, { type: "JOIN", joinOn: ["b", "c"] });
    const registered = [
      fork([[at("b", 3), at("b2", 4)], [at("c", 5)]]),
      join,
      at("d", 7),
    ];
    const shown = withWhatIsAhead(
      [fork([[at("b", 3)], [at("c", 5)]]), join],
      registered,
    );
    expect(shown.map((t) => t.taskReferenceName)).toEqual([
      "fork",
      "fork_join",
      "d",
    ]);
    expect(
      shown[0].forkTasks.map((b: any[]) => b.map((t) => t.taskReferenceName)),
    ).toEqual([["b", "b2"], ["c"]]);
  });

  it("names a preview apart from a step that ran under its reference", () => {
    // a loop: the second pass ran check_2 at line 3, the registered check_2 is at line 5
    const registered = [
      at("check", 3, { metadata: { loops: ["L"] } }),
      at("check_2", 5),
    ];
    const own = [
      at("check", 3, { metadata: { loops: ["L"] } }),
      at("check_2", 3, { metadata: { loops: ["L"] } }),
    ];
    const shown = withWhatIsAhead(own, registered);
    expect(shown.map((t) => t.taskReferenceName)).toEqual([
      "check",
      "check_2",
      "check_2_next",
    ]);
  });

  it("leaves a list whose last step the registered definition does not have as it is", () => {
    const own = [at("a", 1), at("z", 99)];
    expect(withWhatIsAhead(own, [at("a", 1), at("b", 2)])).toEqual(own);
  });
});
