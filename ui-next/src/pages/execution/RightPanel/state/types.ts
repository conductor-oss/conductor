import { DoneInvokeEvent } from "xstate";
import {
  ExecutionTask,
  AuthHeaders,
  TaskLog,
  WorkflowExecution,
  DoWhileSelection,
} from "types";
import { StatusMap } from "pages/execution/state/StatusMapTypes";

export enum RightPanelStates {
  IDLE = "IDLE",
  UPDATE_TASK_STATUS = "UPDATE_TASK_STATUS",
  FETCH_SELECTED_TASK_LOGS = "FETCH_SELECTED_TASK_LOGS",
  FETCH_AFTER = "FETCH_AFTER",
  RERUN_WORKFLOW_FROM_TASK = "RERUN_WORKFLOW_FROM_TASK",
  DETAILED_SECTION = "DETAILED_SECTION",
  END = "END",
  SUMMARY = "SUMMARY",
  INPUT = "INPUT",
  OUTPUT = "OUTPUT",
  LOGS = "LOGS",
  JSON = "JSON",
  DEFINITION = "DEFINITION",
}

export enum RightPanelContextEventTypes {
  SET_SELECTED_TASK = "SET_SELECTED_TASK",
  CLOSE_RIGHT_PANEL = "CLOSE_RIGHT_PANEL",
  UPDATE_SELECTED_TASK_STATUS = "UPDATE_SELECTED_TASK_STATUS",
  SET_UPDATED_EXECUTION = "SET_UPDATED_EXECUTION",
  FETCH_FOR_LOGS = "FETCH_FOR_LOGS",
  RE_RUN_WORKFLOW_FROM_TASK = "RE_RUN_WORKFLOW_FROM_TASK",
  CLEAR_ERROR_MESSAGE = "CLEAR_ERROR_MESSAGE",
  CHANGE_CURRENT_TAB = "CHANGE_CURRENT_TAB",
  SET_DO_WHILE_ITERATION = "SET_DO_WHILE_ITERATION",
}

export type UpdateSelectedTaskStatus = {
  type: RightPanelContextEventTypes.UPDATE_SELECTED_TASK_STATUS;
  payload: {
    status: string;
    body: string;
  };
};

export type ReRunWorkflowFromTaskEvent = {
  type: RightPanelContextEventTypes.RE_RUN_WORKFLOW_FROM_TASK;
};

export type SelectedTaskType = ExecutionTask & {
  selectedIteration?: ExecutionTask;
  iteration?: number;
};

export interface RightPanelContext {
  selectedTask?: ExecutionTask;
  executionStatusMap?: StatusMap;
  taskDetails?: ExecutionTask;
  authHeaders?: AuthHeaders;
  executionId?: string;
  taskLogs?: TaskLog[];
  error?: string;
  currentTab: number;
}

export type SetSelectedTaskEvent = {
  type: RightPanelContextEventTypes.SET_SELECTED_TASK;
  selectedTask: ExecutionTask;
};

export type CloseRightPanelEvent = {
  type: RightPanelContextEventTypes.CLOSE_RIGHT_PANEL;
};

export type UpdateTaskLogsEvent = {
  type: RightPanelContextEventTypes.FETCH_FOR_LOGS;
  data: TaskLog[];
};

export type ClearErrorMessageEvent = {
  type: RightPanelContextEventTypes.CLEAR_ERROR_MESSAGE;
};

export type ChangeCurrentTabEvent = {
  type: RightPanelContextEventTypes.CHANGE_CURRENT_TAB;
  currentTab: number;
};

export type SetUpdatedExecutionEvent = {
  type: RightPanelContextEventTypes.SET_UPDATED_EXECUTION;
  execution: WorkflowExecution;
  executionStatusMap: StatusMap;
};

export type SetDoWhileIterationEvent = {
  type: RightPanelContextEventTypes.SET_DO_WHILE_ITERATION;
  data: DoWhileSelection;
};

export type RightPanelEvents =
  | UpdateSelectedTaskStatus
  | CloseRightPanelEvent
  | UpdateTaskLogsEvent
  | DoneInvokeEvent<ExecutionTask>
  | ReRunWorkflowFromTaskEvent
  | ClearErrorMessageEvent
  | SetSelectedTaskEvent
  | SetUpdatedExecutionEvent
  | ChangeCurrentTabEvent
  | SetDoWhileIterationEvent;
