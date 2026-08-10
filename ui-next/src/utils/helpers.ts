import _isInteger from "lodash/isInteger";
import { HumanTaskState as TaskState } from "types/HumanTaskTypes";
import { colors } from "theme/tokens/variables";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import {
  FIELD_TYPE_BOOLEAN,
  FIELD_TYPE_NULL,
  FIELD_TYPE_NUMBER,
  FIELD_TYPE_OBJECT,
  FIELD_TYPE_STRING,
  FieldType,
} from "types/common";
import { WORKFLOW_SCHEDULE_EXECUTION_STATE } from "utils/constants/workflowScheduleExecution";

export function isFailedTask(status: TaskStatus) {
  return (
    status === "FAILED" ||
    status === "FAILED_WITH_TERMINAL_ERROR" ||
    status === "TIMED_OUT" ||
    status === "CANCELED"
  );
}

/**
 * Create data table title via search result
 * @param {array} filteredData: data after filtering or searching
 * @param {array} data: data of table
 * @returns {string}
 */
export function createTableTitle({
  filteredData = [],
  data = [],
}: {
  filteredData: any[];
  data: any[];
}): string {
  return filteredData.length === data.length
    ? `${filteredData.length} results`
    : `${filteredData.length} results (${
        data.length - filteredData.length
      } not shown)`;
}

export function juxt<T extends readonly unknown[]>(
  ...fns: readonly ((...args: T) => unknown)[]
): (...args: T) => unknown[] {
  return (...args: T): unknown[] => {
    return fns.map((fn) => fn(...args));
  };
}

/**
 * Download file
 * @param {object} data
 * @param {string} fileName
 * @param {string} type
 */
export type ExportableObject = {
  data: Record<string, unknown>;
  fileName: string;
  type: string;
};

export const exportObjToFile = ({
  data,
  fileName,
  type = "application/json",
}: ExportableObject) => {
  const a = window.document.createElement("a");

  a.href = window.URL.createObjectURL(
    new Blob([JSON.stringify(data, null, 2)], {
      type,
    }),
  );
  a.download = fileName;

  // Append anchor to body.
  document.body.appendChild(a);
  a.click();

  // Remove anchor from body
  document.body.removeChild(a);
};

const statusColor = {
  success: colors.successTag,
  progress: colors.progressTag,
  error: colors.errorTag,
  warning: colors.warningTag,
} as const;

/**
 * Get color for rendering chip status
 * @param {string} status: item's status (ex: workflow's status...)
 * @returns {string}
 */
export const getChipStatusColor = (
  status: TaskStatus | WorkflowExecutionStatus | TaskState,
) => {
  switch (status) {
    case WorkflowExecutionStatus.RUNNING:
    case TaskStatus.IN_PROGRESS:
    case TaskStatus.SCHEDULED:
    case TaskState.ASSIGNED:
    case WORKFLOW_SCHEDULE_EXECUTION_STATE.POLLED:
      return statusColor.progress;

    case WorkflowExecutionStatus.COMPLETED:
    case TaskStatus.COMPLETED:
    case WORKFLOW_SCHEDULE_EXECUTION_STATE.EXECUTED:
      return statusColor.success;

    case WorkflowExecutionStatus.PAUSED:
    case TaskStatus.SKIPPED:
    case TaskStatus.CANCELED:
    case TaskStatus.PENDING:
      return statusColor.warning;

    default:
      return statusColor.error;
  }
};

/**
 * Open link in new tab
 * @param {string} url
 */
export const openInNewTab = (url: string) => {
  const newWindow = window.open(url, "_blank", "noopener,noreferrer");

  if (newWindow) newWindow.opener = null;
};

export const inferType: (value: any) => FieldType = (value: any) => {
  if (value === null) {
    return FIELD_TYPE_NULL;
  }

  if (typeof value === "number") {
    return FIELD_TYPE_NUMBER;
  }

  if (typeof value === "object") {
    return FIELD_TYPE_OBJECT;
  }

  if (typeof value === "boolean") {
    return FIELD_TYPE_BOOLEAN;
  }

  return FIELD_TYPE_STRING;
};

export type ValueInputDefaultValues = Partial<Record<FieldType, unknown>>;

export const DEFAULT_FIELD_VALUES_CONF: Record<FieldType, unknown> = {
  [FIELD_TYPE_STRING]: "",
  [FIELD_TYPE_NUMBER]: 0,
  [FIELD_TYPE_OBJECT]: {},
  [FIELD_TYPE_BOOLEAN]: false,
  [FIELD_TYPE_NULL]: null,
};

export const castToType = (
  value: any,
  type: FieldType,
  defaultValuesProvided: ValueInputDefaultValues = DEFAULT_FIELD_VALUES_CONF,
) => {
  const defaultValues = {
    ...DEFAULT_FIELD_VALUES_CONF,
    ...defaultValuesProvided,
  };
  if (type === FIELD_TYPE_NUMBER) {
    try {
      if (!isNaN(value)) {
        return Number(value);
      }

      return defaultValues[FIELD_TYPE_NUMBER];
    } catch {
      return defaultValues[FIELD_TYPE_NUMBER];
    }
  }

  if (type === FIELD_TYPE_OBJECT) {
    try {
      return typeof value !== "boolean" && value !== null && isNaN(value)
        ? JSON.parse(value)
        : defaultValues[FIELD_TYPE_OBJECT];
    } catch {
      return defaultValues[FIELD_TYPE_OBJECT];
    }
  }

  if (type === FIELD_TYPE_BOOLEAN) {
    return Boolean(value);
  }

  if (type === FIELD_TYPE_NULL) {
    return null;
  }
  return typeof value === "string" ? value : defaultValues[FIELD_TYPE_STRING];
};

export const checkCoerceTypeError = ({
  value,
  coerceTo,
}: {
  value: any;
  coerceTo: any;
}) => {
  // Don't check reference string
  if (typeof value === "string" && value.startsWith("${")) {
    return false;
  }

  const tempValue = castToType(
    value,
    isNaN(value as any) ? FIELD_TYPE_STRING : FIELD_TYPE_NUMBER,
  );
  const valueType = inferType(tempValue);

  const isIntegerValue = _isInteger(tempValue);

  if (coerceTo === "integer") {
    return !isIntegerValue;
  }

  if (coerceTo === "double") {
    return valueType !== FIELD_TYPE_NUMBER;
  }

  return false;
};

export function replacePathPlaceholdersToWorkflowInput(path: string): string {
  return path.replace(/\{(\w+)\}/g, (_, key) => `\${workflow.input.${key}}`);
}

export const parseErrorResponse = async ({
  response,
  module,
  operation = "performing this operation",
}: {
  response: Response;
  module: string;
  operation?: string;
}): Promise<string> => {
  try {
    const json = await response?.json();
    if (json?.message) {
      return json.message;
    } else {
      return `An error occurred while ${operation} on ${module}`;
    }
  } catch {
    return `An error occurred while ${operation} on ${module}`;
  }
};
