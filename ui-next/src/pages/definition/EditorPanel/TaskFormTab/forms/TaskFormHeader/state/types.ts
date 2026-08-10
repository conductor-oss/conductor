import { TaskType } from "types";

export interface TaskFormHeaderMachineContext {
  name: string;
  taskReferenceName: string;
  taskType: TaskType;
  isEditingValues?: boolean;
}

export enum TaskFormHeaderEventTypes {
  CHANGE_NAME_VALUE = "CHANGE_NAME_VALUE",
  CHANGE_TASK_REFERENCE_VALUE = "CHANGE_TASK_REFERENCE_VALUE",
  VALUES_UPDATED = "VALUES_UPDATED",
  GENERATE_TASK_REFERENCE_NAME = "GENERATE_TASK_REFERENCE_NAME",
  TASK_CREATED_SUCCESSFULLY = "TASK_CREATED_SUCCESSFULLY",
  START_EDITING_VALUES = "START_EDITING_VALUES",
  STOP_EDITING_VALUES = "STOP_EDITING_VALUES",
}

export type StartEditingValuesEvent = {
  type: TaskFormHeaderEventTypes.START_EDITING_VALUES;
};
export type StopEditingValuesEvent = {
  type: TaskFormHeaderEventTypes.STOP_EDITING_VALUES;
};
export type ChangeNameValueEvent = {
  type: TaskFormHeaderEventTypes.CHANGE_NAME_VALUE;
  value: string;
};

export type ChangeTaskReferenceNameValueEvent = {
  type: TaskFormHeaderEventTypes.CHANGE_TASK_REFERENCE_VALUE;
  value: string;
};

export type ValuesUpdatedEvent = {
  type: TaskFormHeaderEventTypes.VALUES_UPDATED;
  taskType: TaskType;
  name: string;
  taskReferenceName: string;
};

export type GenerateTaskNameReferenceNameEvent = {
  type: TaskFormHeaderEventTypes.GENERATE_TASK_REFERENCE_NAME;
};

export type TaskCreatedSucceffullyEvent = {
  type: TaskFormHeaderEventTypes.TASK_CREATED_SUCCESSFULLY;
};

export type TaskHeaderMachineEvents =
  | ChangeNameValueEvent
  | ChangeTaskReferenceNameValueEvent
  | GenerateTaskNameReferenceNameEvent
  | ValuesUpdatedEvent
  | TaskCreatedSucceffullyEvent
  | StartEditingValuesEvent
  | StopEditingValuesEvent;
