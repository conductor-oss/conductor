import { DoneInvokeEvent } from "xstate";
import { ExecutionTask, AuthHeaders, TaskStatus } from "types";

export type SelectableStatus = TaskStatus;
export type TaskListMachineContext = {
  taskList: ExecutionTask[];
  startIndex: number;
  rowsPerPage: number;
  executionId?: string;
  authHeaders?: AuthHeaders;
  filterStatus?: SelectableStatus[];
  summary?: Record<SelectableStatus, number>;
  totalHits?: number;
};

export enum TaskListMachineEventTypes {
  SET_STATUS_FILTER = "SET_STATUS_FILTER",
  NEXT_PAGE = "NEXT_PAGE",
  CHANGE_ROWS_PER_PAGE = "CHANGE_ROWS_PER_PAGE",
  SEND_SELECTION_TO_PARENT = "SEND_SELECTION_TO_PARENT",
}

export type StatusFilterChangeEvent = {
  type: TaskListMachineEventTypes.SET_STATUS_FILTER;
  status?: SelectableStatus[];
};

export type NextPageEvent = {
  type: TaskListMachineEventTypes.NEXT_PAGE;
  page: number;
};

export type ChangeRowsPerPageEvent = {
  type: TaskListMachineEventTypes.CHANGE_ROWS_PER_PAGE;
  rowsPerPage: number;
};

export type SendSelectionToParentEvent = {
  type: TaskListMachineEventTypes.SEND_SELECTION_TO_PARENT;
  selectedTask: ExecutionTask;
};

export type TaskListPageResponse = {
  results: ExecutionTask[];
  totalHits: number;
  summary: Record<SelectableStatus, number>;
};

export type TaskListMachineEvents =
  | DoneInvokeEvent<TaskListPageResponse>
  | SendSelectionToParentEvent
  | NextPageEvent
  | ChangeRowsPerPageEvent
  | StatusFilterChangeEvent;
