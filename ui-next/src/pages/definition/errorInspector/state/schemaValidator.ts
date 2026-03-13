import type { ErrorObject } from "ajv";
import Ajv from "ajv";
import ajvErrors from "ajv-errors";
import _path from "lodash/fp/path";
import _groupBy from "lodash/groupBy";
import _isEmpty from "lodash/isEmpty";
import _isNil from "lodash/isNil";
import {
  TaskDef,
  WorkflowDef,
  schemasByType,
  workflowDefinitionSchemaWithDepsAjv,
  workflowSchemaAjv,
} from "types";
import { logger } from "utils";
import {
  ErrorIds,
  ErrorSeverity,
  ErrorTypes,
  SchemaStringValidationResponse,
  SchemaValidationResponse,
  TaskErrors,
  ValidationError,
} from "./types";

const taskIndexRegeEx = new RegExp("/tasks/([0-9]{1,})/*");

const ajv = new Ajv({
  schemas: workflowDefinitionSchemaWithDepsAjv,
  allErrors: true,
  allowUnionTypes: true,
});

// Ajv option allErrors is required
ajvErrors(ajv /*, {singleError: true} */);

export const identifyErrorLocation = (validateInstance: any) => {
  return _groupBy(validateInstance.errors, ({ instancePath }) =>
    instancePath.startsWith("/tasks/") ? "workflowTasks" : "workflowRoot",
  );
};

export const extractTaskReferenceNameFromTaskErrors = (
  taskErrrors: ErrorObject[],
  workflow: Partial<WorkflowDef>,
): TaskDef[] => {
  const indexesAndTasks = taskErrrors.reduce((acc, { instancePath }) => {
    const match = taskIndexRegeEx.exec(instancePath);
    return match != null && match?.length > 1
      ? { ...acc, [match[1]]: workflow.tasks![parseInt(match[1])] } // TODO it is true that its possibly undefined
      : acc;
  }, {});

  return Object.values(indexesAndTasks);
};

export const truncateToLastNumber = (str: string) => {
  // Match any sequence up to the last sequence of numbers
  const match = str.match(/^(.*\/\d+)(?:\/|$)/);
  return match ? match[1] : str;
};

export const convertToPropertyPath = (str: string) =>
  str
    .split("/")
    .filter((segment) => segment !== "") // Remove empty segments, e.g., the first one from "/decisionCases/..."
    .map((segment) => (isNaN(Number(segment)) ? `.${segment}` : `[${segment}]`)) // Convert to dot or array notation
    .join(""); // Join the segments

const isUnsupportedType = (error: ErrorObject, originalTask: TaskDef) => {
  const taskInstancePath = truncateToLastNumber(error.instancePath);
  const path = convertToPropertyPath(taskInstancePath);
  const maybeTask = _path(path, originalTask);

  return maybeTask?.type in schemasByType === false;
};

export const taskErrorToValidationError = (
  errorObj: ErrorObject,
  originalTask: TaskDef,
): ValidationError => {
  if (_isEmpty(errorObj.instancePath)) {
    // Error in outer task
    if (errorObj.keyword === "required") {
      return {
        id: ErrorIds.TASK_REQUIRED_FIELD_MISSING,
        message: errorObj?.message ?? "",
        hint: `Add the required field ${errorObj?.params?.missingProperty}`,
        taskReferenceName: originalTask?.taskReferenceName,
        type: ErrorTypes.TASK,
        path: errorObj.instancePath,
        severity: ErrorSeverity.ERROR,
      };
    }
  }

  if (
    errorObj.instancePath !== "/name" &&
    isUnsupportedType(errorObj, originalTask)
  ) {
    return {
      id: ErrorIds.UNKNOWN_TASK_TYPE,
      message: errorObj?.message ?? "",
      taskReferenceName: originalTask?.taskReferenceName,
      path: errorObj.instancePath,
      type: ErrorTypes.TASK,
      severity: ErrorSeverity.ERROR,
    };
  }

  if (errorObj.instancePath.includes("inputParameters")) {
    if (errorObj.keyword === "required") {
      return {
        id: ErrorIds.TASK_REQUIRED_INPUT_PARAMETERS_MISSING,
        message: errorObj?.message ?? "",
        hint: `Add the required inputParameter ${errorObj?.params?.missingProperty}`,
        taskReferenceName: originalTask?.taskReferenceName,
        path: errorObj.instancePath,
        type: ErrorTypes.TASK,
        severity: ErrorSeverity.ERROR,
      };
    }
  }

  if (errorObj.keyword === "enum") {
    return {
      id: ErrorIds.ALLOWED_VALUES,
      message: errorObj?.message ?? "",
      hint: `Use any of the allowed values ${(
        errorObj?.params?.allowedValues || []
      ).join(", ")}`,
      taskReferenceName: originalTask?.taskReferenceName,
      path: errorObj.instancePath,
      type: ErrorTypes.TASK,
      severity: ErrorSeverity.ERROR,
    };
  }

  return {
    id: ErrorIds.GENERIC_ERROR,
    message: errorObj?.message ?? "",
    taskReferenceName: originalTask?.taskReferenceName,
    type: ErrorTypes.TASK,
    severity: ErrorSeverity.ERROR,
  };
};

export const findTaskError = (task: TaskDef): ValidationError[] => {
  const taskType = task.type;

  if (_isNil(taskType)) {
    return [
      {
        id: ErrorIds.TASK_TYPE_NOT_PRESENT,
        message:
          "Every task should have a type attribute, you seem to have missed the type",
        hint: "Add the type: attribute to the task with a supported type.",
        type: ErrorTypes.TASK,

        severity: ErrorSeverity.ERROR,
      },
    ];
  }
  const taskSchema = schemasByType[taskType];
  if (_isNil(taskSchema)) {
    return [
      {
        id: ErrorIds.UNKNOWN_TASK_TYPE,
        message: `Task type ${taskType} is not supported`,
        hint: "Use a supported task type",
        type: ErrorTypes.TASK,
        severity: ErrorSeverity.ERROR,
      },
    ];
  }
  const validate = ajv.getSchema(taskSchema.$id)!;
  const valid = validate(task);
  return valid
    ? []
    : validate.errors?.map((eo) => taskErrorToValidationError(eo, task)) || [];
};

export const createMainValidator = (workflow: Partial<WorkflowDef>): any => {
  const validate = ajv.getSchema(workflowSchemaAjv.$id)!;
  const valid = validate(workflow);

  return valid ? null : validate;
};

export const computeWorkflowStringErrors = (
  workflowString: string,
): SchemaStringValidationResponse => {
  try {
    const workflow: Partial<WorkflowDef> = JSON.parse(workflowString);
    return {
      ...computeWorkflowErrors(workflow),
      currentWf: workflow,
    };
  } catch (err: any) {
    const error = err as Error;
    logger.info("The error is ", err);
    const errorHint = getJSONParseErrorHint(error?.message);

    return {
      taskErrors: [],
      workflowErrors: [
        {
          id: ErrorIds.SERIALIZATION_ERROR,
          message: `JSON has a **syntax** error: ${error?.message}`,
          hint: errorHint,
          type: ErrorTypes.WORKFLOW,
          severity: ErrorSeverity.ERROR,
        },
      ],
    };
  }
};

const getJSONParseErrorHint = (errorMessage?: string): string => {
  const DEFAULT_ERROR_MESSAGE =
    "Workflow definition contains JSON syntax errors. Please check the code tab.";
  if (!errorMessage) {
    return DEFAULT_ERROR_MESSAGE;
  }

  const LOWER_ERROR = errorMessage.toLowerCase();

  const errorPatterns = {
    "unexpected end":
      "Workflow definition is incomplete. Please check for missing closing brackets (}) or braces (]) in your JSON.",
    "expected property name":
      "Malformed workflow definition. Please ensure all property names are properly quoted and check for trailing commas.",
    "trailing comma":
      "Workflow definition contains invalid trailing commas. Please remove them from objects or arrays.",
    "bad control character":
      "Invalid character sequence detected in workflow definition. Please check special characters in your JSON strings.",
    "invalid escape":
      "Invalid character sequence detected in workflow definition. Please check special characters in your JSON strings.",
    "duplicate key":
      "Workflow definition contains duplicate property names. Each property name in a JSON object must be unique.",
    "unexpected number":
      "Unexpected number format in workflow definition. Please check for missing quotes around property names or values.",
    "expected double-quoted property name":
      "Property names in workflow definition must be enclosed in double quotes. Please check your JSON syntax.",
    "unterminated string":
      "Workflow definition contains an unterminated string. Please check for missing closing quotes in your JSON.",
    "unexpected character":
      "Unexpected character in workflow definition. Please check for invalid syntax or characters in your JSON.",
  };

  if (LOWER_ERROR.includes("unexpected token") && LOWER_ERROR.includes("'<'")) {
    return "Invalid character detected in workflow definition. Please ensure your workflow is defined in valid JSON format.";
  }

  if (LOWER_ERROR.includes("unexpected token")) {
    return "Syntax error detected in workflow definition. Please check for missing commas, colons, or invalid characters.";
  }

  for (const [pattern, hint] of Object.entries(errorPatterns)) {
    if (LOWER_ERROR.includes(pattern)) {
      return hint;
    }
  }

  return DEFAULT_ERROR_MESSAGE;
};

export const computeWorkflowErrors = (
  workflow: Partial<WorkflowDef>,
): SchemaValidationResponse => {
  const mainValidator = createMainValidator(workflow);
  if (_isNil(mainValidator)) {
    return {
      taskErrors: [],
      workflowErrors: [],
    };
  }
  // Group error types
  const groupedErrors = identifyErrorLocation(mainValidator);
  // Identified workflow errors not related to tasks
  const workflowErrors = groupedErrors.workflowRoot || [];
  // Tasks containing errors
  const tasksWithProblems = extractTaskReferenceNameFromTaskErrors(
    groupedErrors.workflowTasks || [],
    workflow,
  );

  // Task errors
  const taskErrors: TaskErrors[] = tasksWithProblems.reduce(
    (acc: TaskErrors[], task: TaskDef) => {
      const errors = findTaskError(task).filter(
        ({ id }) => id !== ErrorIds.UNKNOWN_TASK_TYPE, // We will filter out this errors
      );
      if (errors.length === 0) return acc;

      return [...acc, { task, errors }];
    },
    [],
  );

  return {
    taskErrors,
    workflowErrors,
  };
};
