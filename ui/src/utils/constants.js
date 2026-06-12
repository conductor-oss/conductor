export const workflowStatuses = [
  "RUNNING",
  "COMPLETED",
  "FAILED",
  "TIMED_OUT",
  "TERMINATED",
  "PAUSED",
];

export const TASK_STATUSES = [
  "IN_PROGRESS",
  "CANCELED",
  "FAILED",
  "FAILED_WITH_TERMINAL_ERROR",
  "COMPLETED",
  "COMPLETED_WITH_ERRORS",
  "SCHEDULED",
  "TIMED_OUT",
  "SKIPPED",
];

export const TASK_TYPES = [
  "ARCHER",
  "DECISION",
  "DO_WHILE",
  "DYNAMIC",
  "DYNIMO",
  "EAAS",
  "EVENT",
  "EXCLUSIVE_JOIN",
  "FORK_JOIN",
  "FORK_JOIN_DYNAMIC",
  "HTTP",
  "INLINE",
  "JOIN",
  "JSON_JQ_TRANSFORM",
  "LAMBDA",
  "SIMPLE",
  "SUB_WORKFLOW",
  "SWITCH",
  "TERMINATE",
  "TITUS",
  "TITUS_TASK",
  "WAIT",
];

export const SEARCH_TASK_TYPES_SET = modifyTaskTypes(TASK_TYPES);

function modifyTaskTypes(taskTypes) {
  const newTaskTypes = taskTypes.filter(
    (ele) => ele !== "FORK_JOIN_DYNAMIC" && ele !== "SIMPLE"
  );
  const fjIdx = newTaskTypes.findIndex((ele) => ele === "FORK_JOIN");
  newTaskTypes[fjIdx] = "FORK";

  return new Set(newTaskTypes);
}
