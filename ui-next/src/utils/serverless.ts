import { CommonTaskDef } from "types/TaskType";
import { TaskType } from "types/common";

/** Legacy Lambda inputs remain business data, even when they contain the new keys. */
export const lambdaIntegrationName = (
  task: CommonTaskDef,
): string | undefined => {
  if (
    task.type !== TaskType.AWS_LAMBDA &&
    !(task.type === TaskType.SIMPLE && task.name === "AWS_LAMBDA")
  ) {
    return undefined;
  }
  const input = (
    task as CommonTaskDef & {
      inputParameters?: Record<string, unknown>;
    }
  ).inputParameters;
  if (
    !input ||
    Object.prototype.hasOwnProperty.call(input, "lambda_function_name")
  ) {
    return undefined;
  }
  const configuration = input.invocationConfiguration;
  if (
    configuration == null ||
    typeof configuration !== "object" ||
    Array.isArray(configuration)
  ) {
    return undefined;
  }
  const name = (configuration as Record<string, unknown>).integrationName;
  return typeof name === "string" && name.trim() !== "" && !name.includes("${")
    ? name
    : undefined;
};
