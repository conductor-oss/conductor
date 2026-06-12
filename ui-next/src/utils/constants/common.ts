import { HTTPMethods } from "types/TaskType";

export const LOCAL_STORAGE_KEY = {
  ROWS_PER_PAGE: "rowsPerPage",
};

export const FORBIDDEN_DELETE_ERROR_MESSAGE =
  "Deletion failed, it looks like you do not have permissions to delete this resource.";

export const FORBIDDEN_PUT_ERROR_MESSAGE =
  "Update failed, it looks like you do not have permissions to update this resource.";

export const FORBIDDEN_POST_ERROR_MESSAGE =
  "Creation failed, it looks like you do not have permissions to create this resource.";

export const FORBIDDEN_GET_ERROR_MESSAGE =
  "It looks like you do not have permissions to get this resource.";

export const generateForbiddenMessage = (method: HTTPMethods) => {
  switch (method) {
    case HTTPMethods.POST:
      return FORBIDDEN_POST_ERROR_MESSAGE;
    case HTTPMethods.PUT:
    case HTTPMethods.PATCH:
      return FORBIDDEN_PUT_ERROR_MESSAGE;
    case HTTPMethods.DELETE:
      return FORBIDDEN_DELETE_ERROR_MESSAGE;
    default:
      return FORBIDDEN_GET_ERROR_MESSAGE;
  }
};

/**
 * output: Feb 21, 2023 12:19 AM
 */
export const FORMAT_TIME_TO_LONG = "MMM d, yyyy hh:mm a";

/**
 * output: 2023-11-16 12:00 AM
 */
export const FORMAT_DATE_TIME_PICKER = "yyyy-MM-dd hh:mm aa";

export const SEARCH_QUERY_PARAM = "search";
export const PAGE_QUERY_PARAM = "page";
export const FILTER_QUERY_PARAM = "filter";
export const ACTIVE_FILTER_QUERY_PARAM = "activeFilter";
export const USER_ROLE_FILTER_QUERY_PARAM = "roleFilter";

export const HTTP_TEST_ENDPOINT =
  "https://orkes-api-tester.orkesconductor.com/api";

export const HOT_KEYS_SIDEBAR = "sidebar";
export const HOT_KEYS_WORKFLOW_DEFINITION = "workflow-definition";

export const TITLE_ALLOWED_CHARS = "^(?!-)[a-zA-Z0-9_-]*$";

export const ALPHANUMERIC_UNDERSCORE_HYPHEN_PATTERN = "^[a-zA-Z0-9_-]*$";
export const WORKFLOW_NAME_ERROR_MESSAGE =
  "The name should contain only letters (both uppercase and lowercase), digits, spaces, and the characters <, >, {, }, #, and -. No other special characters are allowed.";

export const TASK_NAME_ERROR_MESSAGE =
  "The name should contain only letters (both uppercase and lowercase), digits, spaces, and the characters <, >, {, }, #, and -. No other special characters are allowed.";

export const HEADER_Z_INDEX = 1000;

export const WORKFLOW_SEARCH_QUERY_SUGGESTIONS = [
  "createTime",
  "updateTime",
  "createdBy",
  "updatedBy",
  "status",
  "endTime",
  "workflowId",
  "parentWorkflowId",
  "parentWorkflowTaskId",
  "output",
  "taskToDomain",
  "priority",
  "variables",
  "lastRetriedTime",
  "history",
  "idempotencyKey",
  "rateLimited",
  "startTime",
  "workflowName",
  "workflowVersion",
  "correlationId",
];

export const TASK_SEARCH_QUERY_SUGGESTIONS = [
  "taskType",
  "referenceTaskName",
  "retryCount",
  "seq",
  "pollCount",
  "taskDefName",
  "startDelayInSeconds",
  "retried",
  "executed",
  "callbackFromWorker",
  "responseTimeoutSeconds",
  "workflowInstanceId",
  "workflowType",
  "taskId",
  "callbackAfterSeconds",
  "workerId",
  "outputData",
];

export enum ButtonPosition {
  TOP = "top",
  RIGHT = "right",
  BOTTOM = "bottom",
  LEFT = "left",
}
