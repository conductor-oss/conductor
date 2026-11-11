import { describe, expect, it } from "vitest";
import { TaskType } from "types";
import { tasksAsNodes } from "./core";

const simple = (ref) => ({
  name: ref,
  taskReferenceName: ref,
  type: TaskType.SIMPLE,
});
const fork = (ref, branches) => ({
  name: ref,
  taskReferenceName: ref,
  type: TaskType.FORK_JOIN,
  forkTasks: branches,
});
const join = (ref, metadata) => ({
  name: ref,
  taskReferenceName: ref,
  type: TaskType.JOIN,
  joinOn: [],
  metadata,
});

const ids = (nodes) => nodes.map((n) => n.id);

describe("native joins in the diagram", () => {
  it("draws a fork nothing joins as just a fork, with the path going on from it", async () => {
    const { nodes, edges } = await tasksAsNodes([
      fork("fork", [[simple("a")]]),
      join("fork_join", { detached: true }),
      simple("b"),
    ]);
    expect(ids(nodes)).toEqual(["fork", "a", "b"]);
    const onward = edges.find((e) => e.to === "b");
    expect(onward.from).toBe("fork");
    expect(onward.fromPort).toBeUndefined();
  });

  it("draws a fork the code joins with its join", async () => {
    const { nodes, edges } = await tasksAsNodes([
      fork("fork", [[simple("a")]]),
      join("fork_join"),
    ]);
    expect(ids(nodes)).toEqual(["fork", "a", "fork_join"]);
    expect(edges.some((e) => e.from === "a" && e.to === "fork_join")).toBe(
      true,
    );
  });

  it("draws a later wait with a line from each branch of the fork it names", async () => {
    const { edges } = await tasksAsNodes([
      fork("fork", [[simple("a")], [simple("c")]]),
      join("fork_join", { detached: true }),
      simple("b"),
      join("fork_wait", { waitForFork: "fork" }),
    ]);
    const into = edges.filter((e) => e.to === "fork_wait").map((e) => e.from);
    expect(into.sort()).toEqual(["a", "b", "c"]);
  });
});
