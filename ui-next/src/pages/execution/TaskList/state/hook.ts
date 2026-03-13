import { useActor, useSelector } from "@xstate/react";
import { ActorRef } from "xstate";
import {
  SelectableStatus,
  TaskListMachineEvents,
  TaskListMachineEventTypes,
} from "./types";
import { ExecutionTask } from "types";

export const useTaskListActor = (
  taskListActor: ActorRef<TaskListMachineEvents>,
) => {
  const [, send] = useActor(taskListActor);

  return [
    {
      taskListPage: useSelector(
        taskListActor,
        (state) => state.context.taskList,
      ),
      statusFilter: useSelector(
        taskListActor,
        (state) => state.context.filterStatus,
      ),
      totalHits: useSelector(taskListActor, (state) => state.context.totalHits),
      isFetching: useSelector(taskListActor, (state) =>
        state.matches("fetchForTasks"),
      ),
      rowsPerPage: useSelector(
        taskListActor,
        (state) => state.context.rowsPerPage,
      ),
      summary: useSelector(taskListActor, (state) => state.context.summary),
    },
    {
      handleChangeStatus: (status?: SelectableStatus[]) => {
        send({
          type: TaskListMachineEventTypes.SET_STATUS_FILTER,
          status,
        });
      },
      handleChangePage: (page: number) => {
        send({
          type: TaskListMachineEventTypes.NEXT_PAGE,
          page,
        });
      },
      handleChangeRowsPerPage: (rowsPerPage: number) => {
        send({
          type: TaskListMachineEventTypes.CHANGE_ROWS_PER_PAGE,
          rowsPerPage,
        });
      },
      handleSelectTask: (selectedTask: ExecutionTask) => {
        send({
          type: TaskListMachineEventTypes.SEND_SELECTION_TO_PARENT,
          selectedTask,
        });
      },
    },
  ];
};
