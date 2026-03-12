import { User } from "types/User";
import { AuthHeaders, TaskDef } from "types/common";

export enum TestTaskButtonTypes {
  CHANGE_VALUE = "CHANGE_VALUE",
  UPDATE_TASK_VARIABLES = "UPDATE_TASK_VARIABLES",
  TEST_TASK = "TEST_TASK",
  SET_TASK_DOMAIN = "SET_TASK_DOMAIN",
  SET_TASK_JSON = "SET_TASK_JSON",
  TOGGLE_TEST_TASK = "TOGGLE_TEST_TASK",
}

export enum TestTaskButtonMachineStates {
  RUN_TEST_TASK = "RUN_TEST_TASK",
}

export type SetTaskJsonEvent = {
  type: TestTaskButtonTypes.SET_TASK_JSON;
  originalTask: Record<string, unknown>;
  taskChanges: Record<string, unknown>;
};
export type UpdateTaskVariablesEvent = {
  type: TestTaskButtonTypes.UPDATE_TASK_VARIABLES;
  inputParameters: Record<string, unknown>;
};
export type SetTaskDomainEvent = {
  type: TestTaskButtonTypes.SET_TASK_DOMAIN;
  domain: string;
};
export type TestTaskEvent = {
  type: TestTaskButtonTypes.TEST_TASK;
};

export type TestTaskButtonEvents =
  | TestTaskEvent
  | SetTaskDomainEvent
  | UpdateTaskVariablesEvent
  | SetTaskJsonEvent;

export interface TestTaskButtonMachineContext {
  originalTask?: Record<string, unknown>;
  taskChanges?: Record<string, unknown>;
  taskDomain?: string;
  testedTaskExecutionResult?: Record<string, unknown>;
  authHeaders: AuthHeaders;
  testExecutionId?: string;
  user?: User;
  tasksList?: Partial<TaskDef>[];
}
