import { generateConnectedAppTask, generateMCPTask } from "./taskGenerator";
import { TaskType } from "types";

describe("Connected App task generation", () => {
  it("creates the exact backend input shape and preserves expression overrides", () => {
    const task = generateConnectedAppTask({
      nameGenerator: () => ({ name: "app", taskReferenceName: "app_ref" }),
    });
    expect(task).toEqual({
      name: "app",
      taskReferenceName: "app_ref",
      type: TaskType.CONNECTED_APP,
      inputParameters: { integrationName: "", operation: "", arguments: {} },
    });
    const dynamic = {
      integrationName: "${workflow.input.integration}",
      operation: "${workflow.input.operation}",
      arguments: "${workflow.input.arguments}",
    };
    expect(
      JSON.parse(
        JSON.stringify(
          generateConnectedAppTask({ overrides: { inputParameters: dynamic } }),
        ),
      ).inputParameters,
    ).toEqual(dynamic);
  });
  it("retains the independent INTEGRATION task generator", () => {
    expect(generateMCPTask({}).type).toBe(TaskType.INTEGRATION);
  });
});
