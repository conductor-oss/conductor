import { lambdaIntegrationName } from "./serverless";
import {
  replaceIntegrationName,
  scanTasksForDependenciesInTasks,
} from "./workflow";
import { CommonTaskDef } from "types";

const task = (inputParameters: unknown, type = "AWS_LAMBDA", name = "invoke") =>
  ({
    type,
    name,
    taskReferenceName: "invoke_ref",
    inputParameters,
  }) as CommonTaskDef;

describe("Lambda integration dependencies", () => {
  it.each([
    [undefined, "AWS_LAMBDA", "invoke"],
    [{}, "AWS_LAMBDA", "invoke"],
    [{ invocationConfiguration: null }, "AWS_LAMBDA", "invoke"],
    [{ invocationConfiguration: [] }, "AWS_LAMBDA", "invoke"],
    [
      { invocationConfiguration: "${workflow.input.config}" },
      "AWS_LAMBDA",
      "invoke",
    ],
    [
      { invocationConfiguration: { integrationName: "" } },
      "AWS_LAMBDA",
      "invoke",
    ],
    [
      { invocationConfiguration: { integrationName: " " } },
      "AWS_LAMBDA",
      "invoke",
    ],
    [
      { invocationConfiguration: { integrationName: 1 } },
      "AWS_LAMBDA",
      "invoke",
    ],
    [
      {
        invocationConfiguration: {
          integrationName: "${workflow.input.integration}",
        },
      },
      "AWS_LAMBDA",
      "invoke",
    ],
    [
      {
        invocationConfiguration: { integrationName: "prod" },
        lambda_function_name: "old",
      },
      "AWS_LAMBDA",
      "invoke",
    ],
    [
      { invocationConfiguration: { integrationName: "prod" } },
      "SIMPLE",
      "other-worker",
    ],
    [
      { invocationConfiguration: { integrationName: "prod" } },
      "HTTP",
      "AWS_LAMBDA",
    ],
  ])(
    "does not interpret business keys, legacy fields, or expressions as static dependencies",
    (input, type, name) => {
      expect(lambdaIntegrationName(task(input, type, name))).toBeUndefined();
    },
  );

  it.each([
    ["AWS_LAMBDA", "invoke"],
    ["SIMPLE", "AWS_LAMBDA"],
  ])("finds integrations for %s/%s", (type, name) => {
    const input = {
      invocationConfiguration: {
        integrationName: "prod",
        functionName: "process",
      },
      payload: { integrationName: "business" },
    };
    const original = task(input, type, name);
    expect(lambdaIntegrationName(original)).toBe("prod");
    expect(
      scanTasksForDependenciesInTasks([original]).integrationNames,
    ).toEqual(["prod"]);
    const updated = replaceIntegrationName(original, "prod", "stage");
    expect(updated).toEqual({
      ...original,
      inputParameters: {
        ...input,
        invocationConfiguration: {
          ...input.invocationConfiguration,
          integrationName: "stage",
        },
      },
    });
    expect(input.invocationConfiguration.integrationName).toBe("prod");
    expect(replaceIntegrationName(original, "different", "stage")).toBe(
      original,
    );
  });
});
