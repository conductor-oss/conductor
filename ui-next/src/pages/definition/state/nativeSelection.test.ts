import { describe, expect, it } from "vitest";
import { processWorkflow } from "components/features/flow/nodes";
import {
  crumbsToTask,
  crumbsToTaskSteps,
} from "components/features/flow/nodes/mapper";
import { definitionAsDrawn } from "pages/execution/state/codeLoops";

const call = (ref: string, loops: string[]) => ({
  name: ref,
  taskReferenceName: ref,
  type: "SIMPLE",
  metadata: { lane: "main", loops },
});

// regions loop > hosts loop > a switch on the host, as a fleet patch is written in Java
const fleet: any = {
  name: "native_fleet",
  version: 1,
  metadata: { native: { class: "operator.FleetWorkflow", method: "run" } },
  tasks: [
    call("inventory", []),
    call("probe", ["regions", "hosts"]),
    {
      name: "if_state",
      taskReferenceName: "if_state",
      type: "SWITCH",
      evaluatorType: "native-code",
      expression: "switchCaseValue",
      inputParameters: { switchCaseValue: "true" },
      decisionCases: { true: [call("patch", ["regions", "hosts"])] },
      defaultCase: [call("page", ["regions", "hosts"])],
      metadata: { lane: "main", loops: ["regions", "hosts"] },
    },
    call("report", ["rounds"]),
  ],
};

describe("selecting a node of a native definition", () => {
  it("opens that node, inside the loops it is drawn in", async () => {
    const { nodes } = await processWorkflow(
      definitionAsDrawn(fleet),
      false,
      false,
      async () => ({}),
      "" as any,
    );
    // the diagram's own nodes (start, end, a switch's merge) are not tasks
    const drawn = (nodes as any[]).filter(
      (n) =>
        n.data?.task?.taskReferenceName &&
        !["TERMINAL", "SWITCH_JOIN"].includes(n.data.task.type),
    );
    expect(drawn.map((n) => n.data.task.taskReferenceName)).toEqual(
      expect.arrayContaining([
        "regions",
        "hosts",
        "if_state",
        "patch",
        "rounds",
      ]),
    );
    for (const node of drawn) {
      const found: any = crumbsToTask(
        node.data.crumbs,
        definitionAsDrawn(fleet).tasks,
      );
      expect(found?.taskReferenceName).toBe(node.data.task.taskReferenceName);
      expect(found?.type).toBe(node.data.task.type);
      // the form is created from the last step of the same path
      const steps: any[] = crumbsToTaskSteps(
        node.data.crumbs,
        definitionAsDrawn(fleet).tasks,
      );
      expect(steps[steps.length - 1]?.taskReferenceName).toBe(
        node.data.task.taskReferenceName,
      );
    }
  });

  it("leaves a JSON workflow's definition as it is", () => {
    const plain = { name: "plain", tasks: [call("a", ["l"])] };
    expect(definitionAsDrawn(plain)).toBe(plain);
  });
});
