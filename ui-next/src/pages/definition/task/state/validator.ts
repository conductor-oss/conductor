import type { ErrorObject } from "ajv";
import Ajv from "ajv";
import ajvErrors from "ajv-errors";
import { parseErrors } from "pages/definition/task/state/helpers";
import {
  TaskRetryLogic,
  TaskTimeoutPolicy,
} from "pages/definition/task/state/types";
import { TaskDefinitionDto } from "types/TaskDefinition";
import { getErrors } from "utils/utils";

const taskSchema = {
  $id: "/properties/task",
  type: "object",
  properties: {
    name: {
      type: "string",
      pattern: "^[\\w-]+$",
      errorMessage: {
        pattern:
          "Don't allow special characters. Normal characters, numbers and hyphens are allowed.",
      },
    },
    description: {
      type: "string",
    },
    retryCount: {
      type: "integer",
    },
    timeoutSeconds: {
      type: "integer",
    },
    timeoutPolicy: {
      type: "string",
      enum: Object.values(TaskTimeoutPolicy),
      errorMessage: {
        type: "must be string.",
        enum: `must be one of allowed values: ${Object.values(
          TaskTimeoutPolicy,
        ).join(" | ")}.`,
      },
    },
    retryLogic: {
      type: "string",
      enum: Object.values(TaskRetryLogic),
      errorMessage: {
        type: "must be string.",
        enum: `must be one of allowed values: ${Object.values(
          TaskRetryLogic,
        ).join(" | ")}.`,
      },
    },
    retryDelaySeconds: {
      type: "integer",
    },
    responseTimeoutSeconds: {
      type: "integer",
    },
    rateLimitPerFrequency: {
      type: "integer",
    },
    rateLimitFrequencyInSeconds: {
      type: "integer",
    },
    ownerEmail: {
      type: "string",
    },
    pollTimeoutSeconds: {
      type: "integer",
    },
    concurrentExecLimit: {
      type: "integer",
    },
    backoffScaleFactor: {
      type: "integer",
    },
    inputKeys: {
      type: "array",
      items: {
        type: "string",
      },
    },
    outputKeys: {
      type: "array",
      items: {
        type: "string",
      },
    },
    inputTemplate: {
      type: "object",
    },
  },
  required: ["name"],
};

const tasksSchema = {
  $id: "/properties/tasks",
  type: "array",
  uniqueItems: true,
  items: {
    $ref: taskSchema.$id,
  },
};

const ajv = new Ajv({
  schemas: [taskSchema, tasksSchema],
  allErrors: true,
});

// Ajv option allErrors is required
ajvErrors(ajv /*, {singleError: true} */);

export const validateTask = (
  task: TaskDefinitionDto | TaskDefinitionDto[],
  isBulk: boolean,
): null | ErrorObject[] => {
  const validate = ajv.compile(isBulk ? tasksSchema : taskSchema);
  const valid = validate(task);

  if (!valid) {
    return validate.errors as ErrorObject[];
  }

  return null;
};

export const validatingService = async (
  modifiedTaskDefinition: TaskDefinitionDto | TaskDefinitionDto[],
  isBulk: boolean,
) => {
  try {
    const errors = validateTask(modifiedTaskDefinition, isBulk);

    return {
      error: parseErrors(errors),
      numberOfError: errors?.length || 0,
    };
  } catch (error: any) {
    const errorDetail = await getErrors(error as Response);

    return Promise.reject({
      message: errorDetail.message
        ? errorDetail.message
        : "Validate task failed!",
    });
  }
};
