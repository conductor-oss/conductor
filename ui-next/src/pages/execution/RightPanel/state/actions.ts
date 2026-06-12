import { assign, sendParent, DoneInvokeEvent, DoneEvent } from "xstate";
import {
  ChangeCurrentTabEvent,
  ClearErrorMessageEvent,
  RightPanelContext,
  SetSelectedTaskEvent,
  SetUpdatedExecutionEvent,
  UpdateTaskLogsEvent,
} from "./types";
import { ExecutionTask } from "types";
import { ExecutionActionTypes } from "../../state/types";
import { taskWithLatestIteration } from "pages/execution/helpers";

export const persistTaskDetails = assign<
  RightPanelContext,
  DoneInvokeEvent<ExecutionTask>
>({
  taskDetails: (_, { data }) => data,
});

export const persistSelectedTask = assign<
  RightPanelContext,
  SetSelectedTaskEvent
>({
  selectedTask: (_, { selectedTask }) => selectedTask,
});

export const notifyTaskUpdateToParent = sendParent(
  ExecutionActionTypes.REFETCH,
);

export const notifySelectedTaskUpdateToParent = sendParent<
  RightPanelContext,
  SetSelectedTaskEvent
>((ctx, _event) => {
  return {
    type: ExecutionActionTypes.UPDATE_TASKID_IN_URL,
    selectedTask: ctx.selectedTask,
  };
});

export const sendDoWhileIterationToParent = sendParent<
  RightPanelContext,
  DoneEvent
>((_ctx, { data }) => {
  return {
    type: ExecutionActionTypes.SET_DO_WHILE_ITERATION,
    data: data,
  };
});

export const sendSelectedTaskToParent = sendParent<
  RightPanelContext,
  SetSelectedTaskEvent
>((_ctx, { selectedTask }) => {
  return {
    type: ExecutionActionTypes.UPDATE_SELECTED_TASK,
    selectedTask: selectedTask,
  };
});

export const updateTaskLogs = assign<RightPanelContext, UpdateTaskLogsEvent>(
  (__context: any, event) => {
    return {
      taskLogs: event?.data,
    };
  },
);
export const persistError = assign<
  RightPanelContext,
  DoneInvokeEvent<{
    errorDetails: any;
    message: string;
  }>
>({
  error: (_context, { data }) => data.errorDetails?.message,
});

export const clearErrorMessage = assign<
  RightPanelContext,
  ClearErrorMessageEvent
>(() => {
  return {
    error: undefined,
  };
});

export const updateCurrentTab = assign<
  RightPanelContext,
  ChangeCurrentTabEvent
>({
  currentTab: (__, { currentTab }) => currentTab,
});

export const extractUpdates = assign<
  RightPanelContext,
  SetUpdatedExecutionEvent
>((context, { execution, executionStatusMap }) => {
  return {
    executionStatusMap,
    selectedTask:
      taskWithLatestIteration(
        execution.tasks,
        context.selectedTask?.referenceTaskName,
        context.selectedTask?.taskId,
      ) || context.selectedTask,
  };
});
