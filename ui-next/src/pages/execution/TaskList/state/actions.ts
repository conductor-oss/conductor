import { assign, DoneInvokeEvent } from "xstate";
import {
  TaskListMachineContext,
  StatusFilterChangeEvent,
  TaskListPageResponse,
  NextPageEvent,
  ChangeRowsPerPageEvent,
  SendSelectionToParentEvent,
} from "./types";
import { RightPanelContextEventTypes } from "pages/execution/RightPanel";
import { sendParent } from "xstate/lib/actions";

export const persistTaskListPage = assign<
  TaskListMachineContext,
  DoneInvokeEvent<TaskListPageResponse>
>({
  taskList: (_context: TaskListMachineContext, { data }) => data.results,
  totalHits: (_context: TaskListMachineContext, { data }) => data.totalHits,
  summary: (_context: TaskListMachineContext, { data }) => data.summary,
});

export const persistFilterStatus = assign<
  TaskListMachineContext,
  StatusFilterChangeEvent
>({
  filterStatus: (_context, { status }) => status,
});

export const persistNextPage = assign<TaskListMachineContext, NextPageEvent>({
  startIndex: ({ rowsPerPage = 15 }, { page }) => (page - 1) * rowsPerPage,
});

export const persistRowPerPage = assign<
  TaskListMachineContext,
  ChangeRowsPerPageEvent
>({
  rowsPerPage: (__, { rowsPerPage }) => rowsPerPage,
});

export const selectTask = sendParent<
  TaskListMachineContext,
  SendSelectionToParentEvent
>((__, { selectedTask }) => ({
  type: RightPanelContextEventTypes.SET_SELECTED_TASK,
  selectedTask,
}));
