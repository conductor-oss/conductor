import { ActorRef } from "xstate";

import { AuthHeaders } from "types";
import { PopoverMessage, TaskDefinitionDto } from "types";
import { User } from "types/User";
import {
  TaskDefinitionFormContext,
  TaskDefinitionFormMachineEvent,
} from "../form/state/types";

export enum TaskTimeoutPolicy {
  RETRY = "RETRY",
  TIME_OUT_WF = "TIME_OUT_WF",
  ALERT_ONLY = "ALERT_ONLY",
}

export const TaskTimeoutPolicyLabel = {
  RETRY: "Retry Task",
  TIME_OUT_WF: "Timeout Workflow",
  ALERT_ONLY: "Alert Only",
} as { [key: string]: string };

export enum TaskRetryLogic {
  FIXED = "FIXED",
  LINEAR_BACKOFF = "LINEAR_BACKOFF",
  EXPONENTIAL_BACKOFF = "EXPONENTIAL_BACKOFF",
}

export const TaskRetryLogicLabel = {
  FIXED: "Fixed",
  LINEAR_BACKOFF: "Linear Backoff",
  EXPONENTIAL_BACKOFF: "Exponential Backoff",
} as { [key: string]: string };

export interface TaskDefinitionButtonsProps {
  taskDefActor: ActorRef<TaskDefinitionMachineEvent>;
  showTestTask?: () => void;
}

export interface TaskDefinitionDiffEditorProps {
  taskDefActor: ActorRef<TaskDefinitionMachineEvent>;
}

export interface TaskDefinitionFormProps {
  formActor: ActorRef<TaskDefinitionFormMachineEvent>;
}

export enum TaskDefinitionMachineState {
  INIT = "init",
  FORM = "form",
  EDITOR = "editor",
  RESET_FORM = "resteForm",
  RESET_FORM_CONFIRM = "resetFormConfirm",
  DELETE_FORM = "deleteForm",
  DELETE_FORM_CONFIRM = "deleteFormConfirm",
  DOWNLOAD_TASK_JSON = "downloadTaskJson",
  MAIN_CONTAINER = "mainContainer",
  TASK_TESTER = "taskTester",
  DIFF_EDITOR = "diffEditor",
  DIFF_EDITOR_CONFIRM = "diffEditorConfirm",
  FINISH = "finish",
  READY = "ready",
  FETCH_FOR_TASK_DEFINITION = "fetchForTaskDefinition",
}

export interface TaskDefinitionMachineContext {
  authHeaders: AuthHeaders;
  error?: { [key: string]: any };
  isContinueCreate?: boolean;
  isNewTaskDef: boolean;
  modifiedTaskDefinitionString: string;
  originTaskDefinitionString: string;
  modifiedTaskDefinition: Partial<TaskDefinitionDto>;
  originTaskDefinition: Partial<TaskDefinitionDto>;
  originTaskDefinitions: Partial<TaskDefinitionDto>[];
  couldNotParseJson: boolean;
  user?: User;
  testInputParameters?: string;
  testTaskDomain?: string;
  testTaskWorkflowId?: string;
  lastSelectedTab?:
    | TaskDefinitionMachineState.FORM
    | TaskDefinitionMachineState.EDITOR;
}

export enum TaskDefinitionMachineEventType {
  CANCEL_CONFIRM_SAVE = "CANCEL_CONFIRM_SAVE",
  CLOSE_DIALOG = "CLOSE_DIALOG",
  DEBOUNCE_HANDLE_CHANGE_TASK_DEFINITION = "DEBOUNCE_HANDLE_CHANGE_TASK_DEFINITION",
  HANDLE_CHANGE_TASK_DEFINITION = "HANDLE_CHANGE_TASK_DEFINITION",
  HANDLE_DEFINE_NEW_CONFIRMATION = "HANDLE_DEFINE_NEW_CONFIRMATION",
  SET_DEFINE_NEW_TASK_OPEN = "SET_DEFINE_NEW_TASK_OPEN",
  HANDLE_DELETE_TASK_DEF_CONFIRMATION = "HANDLE_DELETE_TASK_DEF_CONFIRMATION",
  HANDLE_RESET_CONFIRMATION = "HANDLE_RESET_CONFIRMATION",
  HANDLE_RUN_TEST_TASK = "HANDLE_RUN_TEST_TASK",
  NEW_SAVE_COMPLETE = "NEW_SAVE_COMPLETE",
  SAVE_TASK_DEFINITION = "SAVE_TASK_DEFINITION",
  SET_DELETE_CONFIRMATION_OPEN = "SET_DELETE_CONFIRMATION_OPEN",
  SET_INPUT_PARAMETERS = "SET_INPUT_PARAMETERS",
  SET_RESET_CONFIRMATION_OPEN = "SET_RESET_CONFIRMATION_OPEN",
  SET_SAVE_CONFIRMATION_OPEN = "SET_SAVE_CONFIRMATION_OPEN",
  SET_TASK_DOMAIN = "SET_TASK_DOMAIN",
  TOGGLE_BULK_MODE = "TOGGLE_BULK_MODE",
  TOGGLE_FORM_MODE = "TOGGLE_FORM_MODE",
  SYNC_DATA_FROM_FORM_MACHINE = "SYNC_DATA_FROM_FORM_MACHINE",
  NEED_SYNC_DATA_FROM_FORM_MACHINE = "NEED_SYNC_DATA_FROM_FORM_MACHINE",
  EXPORT_TASK_TO_JSON_FILE = "EXPORT_TASK_TO_JSON_FILE",
  CONFIRM_DELETE_TASK = "CONFIRM_DELETE_TASK",
  CONFIRM_GO_TO_DEFINE_NEW_TASK = "CONFIRM_GO_TO_DEFINE_NEW_TASK",
  CONFIRM_RESET_TASK = "CONFIRM_RESET_TASK",
  SET_TASK_DEFINITION = "SET_TASK_DEFINITION",
}

export type NewSaveCompleteEvent = {
  isContinueCreate: boolean;
  isNewTaskDef: boolean;
  modifiedTaskDefinition: TaskDefinitionDto;
  popoverMessage: PopoverMessage;
  saveComplete: boolean;
  saveConfirmationOpen: boolean;
  taskIsModified: boolean;
  type: TaskDefinitionMachineEventType.NEW_SAVE_COMPLETE;
};

export type HandleChangeTaskDefinitionEvent = {
  type: TaskDefinitionMachineEventType.HANDLE_CHANGE_TASK_DEFINITION;
  modifiedTaskDefinitionString: string;
};

export type DebounceHandleChangeTaskDefinitionEvent = {
  type: TaskDefinitionMachineEventType.DEBOUNCE_HANDLE_CHANGE_TASK_DEFINITION;
  modifiedTaskDefinitionString: string;
};

export type HandleResetConfirmationEvent = {
  type: TaskDefinitionMachineEventType.HANDLE_RESET_CONFIRMATION;
  isConfirm: boolean;
};

export type HandleDefineNewConfirmationEvent = {
  type: TaskDefinitionMachineEventType.HANDLE_DEFINE_NEW_CONFIRMATION;
  isConfirm: boolean;
};

export type HandleDeleteTaskDefConfirmationEvent = {
  type: TaskDefinitionMachineEventType.HANDLE_DELETE_TASK_DEF_CONFIRMATION;
  isConfirm: boolean;
};

export type HandleRunTestTaskEvent = {
  type: TaskDefinitionMachineEventType.HANDLE_RUN_TEST_TASK;
};

export type HandleDefineNewTaskEvent = {
  type: TaskDefinitionMachineEventType.SET_DEFINE_NEW_TASK_OPEN;
};

export type SaveTaskDefinitionEvent = {
  type: TaskDefinitionMachineEventType.SAVE_TASK_DEFINITION;
};

export type SetSaveConfirmationOpenEvent = {
  type: TaskDefinitionMachineEventType.SET_SAVE_CONFIRMATION_OPEN;
  isContinueCreate: boolean;
};

export type SetDeleteConfirmationOpenEvent = {
  type: TaskDefinitionMachineEventType.SET_DELETE_CONFIRMATION_OPEN;
};

export type SetResetConfirmationOpenEvent = {
  type: TaskDefinitionMachineEventType.SET_RESET_CONFIRMATION_OPEN;
};

export type CancelConfirmSaveEvent = {
  type: TaskDefinitionMachineEventType.CANCEL_CONFIRM_SAVE;
};

export type CloseDialogEvent = {
  type: TaskDefinitionMachineEventType.CLOSE_DIALOG;
};

export type ToggleBulkModeEvent = {
  type: TaskDefinitionMachineEventType.TOGGLE_BULK_MODE;
  bulkMode: boolean;
};

export type SetInputParametersEvent = {
  type: TaskDefinitionMachineEventType.SET_INPUT_PARAMETERS;
  inputParameters: string;
};

export type SetTaskDomainEvent = {
  type: TaskDefinitionMachineEventType.SET_TASK_DOMAIN;
  domain: string;
};

export type ToggleFormModeEvent = {
  type: TaskDefinitionMachineEventType.TOGGLE_FORM_MODE;
  formMode: boolean;
};

export type SyncDataFromFormMachine = {
  type: TaskDefinitionMachineEventType.SYNC_DATA_FROM_FORM_MACHINE;
  data: TaskDefinitionFormContext;
};

export type NeedSyncDataFromFormMachineEvent = {
  type: TaskDefinitionMachineEventType.NEED_SYNC_DATA_FROM_FORM_MACHINE;
};

export type ExportTaskToJSONFileEvent = {
  type: TaskDefinitionMachineEventType.EXPORT_TASK_TO_JSON_FILE;
};

export type ConfirmDeleteTaskEvent = {
  type: TaskDefinitionMachineEventType.CONFIRM_DELETE_TASK;
};

export type ConfirmGoToDefineNewTask = {
  type: TaskDefinitionMachineEventType.CONFIRM_GO_TO_DEFINE_NEW_TASK;
};

export type ConfirmResetTaskEvent = {
  type: TaskDefinitionMachineEventType.CONFIRM_RESET_TASK;
};

export type SetTaskDefinitionEvent = {
  type: TaskDefinitionMachineEventType.SET_TASK_DEFINITION;
  name: string;
  isNew: boolean;
};

export type TaskDefinitionMachineEvent =
  | CancelConfirmSaveEvent
  | CloseDialogEvent
  | ConfirmDeleteTaskEvent
  | ConfirmGoToDefineNewTask
  | ConfirmResetTaskEvent
  | DebounceHandleChangeTaskDefinitionEvent
  | ExportTaskToJSONFileEvent
  | HandleChangeTaskDefinitionEvent
  | HandleDefineNewConfirmationEvent
  | HandleDefineNewTaskEvent
  | HandleDeleteTaskDefConfirmationEvent
  | HandleResetConfirmationEvent
  | HandleRunTestTaskEvent
  | NeedSyncDataFromFormMachineEvent
  | NewSaveCompleteEvent
  | SaveTaskDefinitionEvent
  | SetDeleteConfirmationOpenEvent
  | SetInputParametersEvent
  | SetResetConfirmationOpenEvent
  | SetSaveConfirmationOpenEvent
  | SetTaskDomainEvent
  | SyncDataFromFormMachine
  | ToggleBulkModeEvent
  | SetTaskDefinitionEvent
  | ToggleFormModeEvent;
