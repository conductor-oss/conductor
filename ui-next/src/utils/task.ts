import {
  CommonTaskDef,
  ForkJoinDynamicDef,
  JDBCTaskDef,
  LLMTaskTypes,
  SetVariableTaskDef,
} from "types/TaskType";
import { TASK_STATUS } from "./constants/task";
import { flipObject } from "./object";
import { TaskType } from "types/common";
import { HumanTaskDef } from "types/HumanTaskTypes";

const taskOrder = [
  TASK_STATUS.SCHEDULED,
  TASK_STATUS.IN_PROGRESS,
  TASK_STATUS.SKIPPED,
  TASK_STATUS.COMPLETED,
  TASK_STATUS.COMPLETED_WITH_ERRORS,
  TASK_STATUS.TIMED_OUT,
  TASK_STATUS.FAILED,
  TASK_STATUS.FAILED_WITH_TERMINAL_ERROR,
];

const arrayAsObject: Record<number, string> = Object.assign({}, taskOrder);

const taskOrderObject = flipObject(arrayAsObject);

export const taskStatusCompareFn = (a: string, b: string) => {
  if (taskOrderObject[a] < taskOrderObject[b]) {
    return -1;
  }
  if (taskOrderObject[a] > taskOrderObject[b]) {
    return 1;
  }
  return 0;
};

const LLMTaskTypesTypes = [
  TaskType.LLM_TEXT_COMPLETE,
  TaskType.LLM_GENERATE_EMBEDDINGS,
  TaskType.LLM_GET_EMBEDDINGS,
  TaskType.LLM_STORE_EMBEDDINGS,
  TaskType.LLM_INDEX_DOCUMENT,
  TaskType.LLM_SEARCH_INDEX,
  TaskType.GET_DOCUMENT,
  TaskType.LLM_INDEX_TEXT,
  TaskType.LLM_CHAT_COMPLETE,
];

export const TaskTypesStrings = Object.values(TaskType);
// Task Predicates
export const isTask = (value: any): value is CommonTaskDef =>
  "type" in value && TaskTypesStrings.includes(value.type);

export const isLLMTask = (task: CommonTaskDef): task is LLMTaskTypes =>
  LLMTaskTypesTypes.includes(task.type);

export const isHumanTask = (task: CommonTaskDef): task is HumanTaskDef =>
  task.type === "HUMAN";

export const isSetVariable = (
  task: CommonTaskDef,
): task is SetVariableTaskDef => task.type === "SET_VARIABLE";

export const isDynamicForkTask = (
  task: CommonTaskDef,
): task is ForkJoinDynamicDef => task.type === "FORK_JOIN_DYNAMIC";

export const isJDBCTask = (task: CommonTaskDef): task is JDBCTaskDef =>
  task.type === "JDBC";
