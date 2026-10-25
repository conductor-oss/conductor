import { generateAWSLambdaTask, taskGeneratorMap } from "./taskGenerator";
import { iconForTaskTypeMap } from "./iconsForTaskTypes";
import { TaskType } from "types";

describe("AWS Lambda template", () => {
  it("creates an integration envelope without legacy keys or a version marker", () => {
    const task = generateAWSLambdaTask();
    expect(task.type).toBe(TaskType.AWS_LAMBDA);
    expect(task.inputParameters).toEqual({
      invocationConfiguration: { integrationName: "", functionName: "" },
      payload: {},
    });
    expect(task.name).toMatch(/^aws_lambda_task_/);
    expect(task.taskReferenceName).toBe(`${task.name}_ref`);
    expect(iconForTaskTypeMap[TaskType.AWS_LAMBDA]).toBeDefined();
    expect(taskGeneratorMap[TaskType.AWS_LAMBDA]).toBe(generateAWSLambdaTask);
  });

  it("honors supplied naming and JSON overrides without mutating them", () => {
    const overrides = {
      inputParameters: {
        invocationConfiguration: {
          integrationName: "prod",
          functionName: "process",
          asyncComplete: false,
        },
        payload: { amount: 42, items: [1, 2] },
      },
    };
    const task = generateAWSLambdaTask({
      nameGenerator: () => ({
        name: "invoke",
        taskReferenceName: "invoke_ref",
      }),
      overrides,
    });
    expect(task).toEqual({
      name: "invoke",
      taskReferenceName: "invoke_ref",
      type: TaskType.AWS_LAMBDA,
      ...overrides,
    });
    expect(task.inputParameters).toBe(overrides.inputParameters);
  });
});
