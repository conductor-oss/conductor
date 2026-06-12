import { useSelector } from "@xstate/react";
import { useMemo } from "react";
import { useQueryState } from "react-router-use-location-state";
import { DoWhileSelection, ExecutionTask } from "types/Execution";
import { ActorRef, State } from "xstate";
import {
  RightPanelContext,
  RightPanelContextEventTypes,
  RightPanelEvents,
  SetSelectedTaskEvent,
} from "./types";
import { TaskStatus } from "types/TaskStatus";

export const useRightPanelActor = (
  rightPanelActor: ActorRef<RightPanelEvents>,
) => {
  const send = rightPanelActor.send;
  const selectedTask = useSelector(
    rightPanelActor,
    (state: State<RightPanelContext>) => {
      const selectedTask = state.context.selectedTask;
      return selectedTask;
    },
  );

  const [_taskId, handleTaskId] = useQueryState<string>("taskId", "");
  const executionStatusMap = useSelector(
    rightPanelActor,
    (state: State<RightPanelContext>) => state.context.executionStatusMap,
  );

  const selectedTaskInStatusMap = useMemo(() => {
    if (selectedTask != null && executionStatusMap != null) {
      const maybeTask =
        executionStatusMap[selectedTask.workflowTask.taskReferenceName];
      return maybeTask;
    }
  }, [executionStatusMap, selectedTask]);

  const retryIterationOptions =
    selectedTaskInStatusMap?.loopOver &&
    [...(selectedTaskInStatusMap?.loopOver ?? [])].reverse();
  const maybeSiblings = selectedTaskInStatusMap?.related?.siblings || [];

  const isSelectedTaskInProgressStatus = useSelector(
    rightPanelActor,
    (state: State<RightPanelContext>) =>
      state.context?.selectedTask?.status &&
      [
        TaskStatus.PENDING,
        TaskStatus.SCHEDULED,
        TaskStatus.IN_PROGRESS,
      ].includes(state?.context?.selectedTask?.status),
  );
  // this condition check is required as there is a backend bug which returns the previous iterations outputdata when rerunning from a task in progress status
  const isSelectedTaskIsARetry = useSelector(
    rightPanelActor,
    (state: State<RightPanelContext>) =>
      state.context?.selectedTask?.retryCount &&
      state.context?.selectedTask?.retryCount > 0,
  );

  return [
    {
      selectedTask,
      retryIterationOptions,
      maybeSiblings,
      isIteration: useSelector(
        rightPanelActor,
        (state: State<RightPanelContext>) =>
          state.context.selectedTask?.loopOverTask,
      ),
      errorMessage: useSelector(
        rightPanelActor,
        (state: State<RightPanelContext>) => state.context.error,
      ),
      taskLogs: useSelector(
        rightPanelActor,
        (state: State<RightPanelContext>) => state?.context?.taskLogs,
      ),
      currentTab: useSelector(
        rightPanelActor,
        (state: State<RightPanelContext>) => state?.context?.currentTab,
      ),
      isReRunFromTaskInProgress:
        isSelectedTaskInProgressStatus && isSelectedTaskIsARetry,
    },
    {
      handleClosePanel: () => {
        send({
          type: RightPanelContextEventTypes.CLOSE_RIGHT_PANEL,
        });
      },
      handleChangeTaskStatus: (status: string, body: string) => {
        send({
          type: RightPanelContextEventTypes.UPDATE_SELECTED_TASK_STATUS,
          payload: {
            status,
            body,
          },
        });
      },
      handleReRunRequest: () => {
        send({
          type: RightPanelContextEventTypes.RE_RUN_WORKFLOW_FROM_TASK,
        });
      },
      clearErrorMessage: () => {
        send({
          type: RightPanelContextEventTypes.CLEAR_ERROR_MESSAGE,
        });
      },
      handleSelectTask: (selectedTask: ExecutionTask) => {
        const selectedTaskEvent: SetSelectedTaskEvent = {
          type: RightPanelContextEventTypes.SET_SELECTED_TASK,
          selectedTask: selectedTask,
        };
        if (selectedTask?.taskId) {
          handleTaskId(selectedTask?.taskId);
        }
        send(selectedTaskEvent);
      },
      handleSelectDoWhileIteration: (data: DoWhileSelection) => {
        send({
          type: RightPanelContextEventTypes.SET_DO_WHILE_ITERATION,
          data: data,
        });
      },
    },
  ] as const;
};
