import { describe, expect, it, vi } from "vitest";
import { withCodePreview } from "./services";

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

describe("withCodePreview", () => {
  it("shows the registered definition while the code has not run", async () => {
    const fetchDefinition = vi.fn().mockResolvedValue(registered);
    const shown = await withCodePreview(execution([]), fetchDefinition);
    expect(fetchDefinition).toHaveBeenCalledWith("loop_shapes", 1);
    expect(shown.codePreview).toBe(true);
    expect(shown.workflowDefinition.tasks).toEqual(registered.tasks);
    expect(shown.workflowDefinition.metadata.native).toBeDefined();
  });

  it("keeps the execution's own definition once the code drew it, or when it ended", async () => {
    const fetchDefinition = vi.fn().mockResolvedValue(registered);
    const started = execution([{ taskReferenceName: "loop_step" }]);
    expect(await withCodePreview(started, fetchDefinition)).toBe(started);
    const ended = execution([], "TERMINATED");
    expect(await withCodePreview(ended, fetchDefinition)).toBe(ended);
    expect(fetchDefinition).not.toHaveBeenCalled();
  });

  it("leaves a workflow that is not native, or a definition it cannot fetch, as it is", async () => {
    const plain = { status: "RUNNING", workflowDefinition: { tasks: [] } };
    expect(await withCodePreview(plain, vi.fn())).toBe(plain);
    const pending = execution([]);
    const failing = vi.fn().mockRejectedValue(new Error("404"));
    expect(await withCodePreview(pending, failing)).toBe(pending);
  });
});
