import { PopoverMessage, TaskDefinitionDto } from "types";

export interface TaskDefinitionFormContext {
  modifiedTaskDefinition: TaskDefinitionDto;
  originTaskDefinition: TaskDefinitionDto;
  error?: { [key: string]: any };
  numberOfError?: number;
  popoverMessage?: PopoverMessage;
}

export enum TaskDefinitionFormEventType {
  HANDLE_CHANGE_TASK_FORM = "HANDLE_CHANGE_TASK_FORM",
  SET_EDITING_FORM_FIELD = "SET_EDITING_FORM_FIELD",
  STOP_FORM_MACHINE = "STOP_FORM_MACHINE",
  SYNC_DATA_TO_PARENT = "SYNC_DATA_TO_PARENT",
  RESET_FORM = "RESET_FORM",
  TOGGLE_FORM_MODE = "TOGGLE_FORM_MODE",
  SET_RESET_CONFIRMATION_OPEN = "SET_RESET_CONFIRMATION_OPEN",
  SET_SAVE_CONFIRMATION_OPEN = "SET_SAVE_CONFIRMATION_OPEN",
  SET_DELETE_CONFIRMATION_OPEN = "SET_DELETE_CONFIRMATION_OPEN",
  EXPORT_TASK_TO_JSON_FILE = "EXPORT_TASK_TO_JSON_FILE",
  CONFIRM_RESET_TASK = "CONFIRM_RESET_TASK",
}

export type ToggleFormModeEvent = {
  type: TaskDefinitionFormEventType.TOGGLE_FORM_MODE;
};

export type SetSaveConfirmationEvent = {
  type: TaskDefinitionFormEventType.SET_SAVE_CONFIRMATION_OPEN;
};

export type SetResetConfirmationEvent = {
  type: TaskDefinitionFormEventType.SET_RESET_CONFIRMATION_OPEN;
};

export type SetDeleteConfirmationEvent = {
  type: TaskDefinitionFormEventType.SET_DELETE_CONFIRMATION_OPEN;
};

export type ExportTaskToJsonEvent = {
  type: TaskDefinitionFormEventType.EXPORT_TASK_TO_JSON_FILE;
};

export type ConfirmResetEvent = {
  type: TaskDefinitionFormEventType.CONFIRM_RESET_TASK;
};

export type SetEditingFormFieldEvent = {
  type: TaskDefinitionFormEventType.SET_EDITING_FORM_FIELD;
  name: string;
};

export type HandleChangeTaskFormEvent = {
  type: TaskDefinitionFormEventType.HANDLE_CHANGE_TASK_FORM;
  name: string;
  value:
    | number
    | string
    | Record<string, string | number>
    | boolean
    | null
    | string[]
    | undefined;
};

export type StopFormMachineEvent = {
  type: TaskDefinitionFormEventType.STOP_FORM_MACHINE;
};

export type SyncDataToParentEvent = {
  type: TaskDefinitionFormEventType.SYNC_DATA_TO_PARENT;
};

export type ResetFormEvent = {
  type: TaskDefinitionFormEventType.RESET_FORM;
};

export type TaskDefinitionFormMachineEvent =
  | HandleChangeTaskFormEvent
  | SetEditingFormFieldEvent
  | StopFormMachineEvent
  | SyncDataToParentEvent
  | ToggleFormModeEvent
  | SetSaveConfirmationEvent
  | SetResetConfirmationEvent
  | SetDeleteConfirmationEvent
  | ExportTaskToJsonEvent
  | ConfirmResetEvent
  | ResetFormEvent;
