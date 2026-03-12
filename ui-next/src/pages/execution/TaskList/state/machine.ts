import { createMachine } from "xstate";
import { TaskListMachineContext, TaskListMachineEventTypes } from "./types";
import * as services from "./services";
import * as actions from "./actions";

export const taskListMachine = () =>
  createMachine<TaskListMachineContext>(
    {
      id: "taskListMachine",
      predictableActionArguments: true,
      initial: "fetchForTasks",
      context: {
        taskList: [],
        startIndex: 0,
        rowsPerPage: 15,
        executionId: undefined,
        authHeaders: undefined,
        filterStatus: undefined,
        totalHits: undefined,
      },
      states: {
        fetchForTasks: {
          invoke: {
            src: "fetchForTasksService",
            onDone: {
              target: "idle",
              actions: ["persistTaskListPage"],
            },
          },
        },
        idle: {
          on: {
            [TaskListMachineEventTypes.SET_STATUS_FILTER]: {
              actions: ["persistFilterStatus"],
              target: "fetchForTasks",
            },
            [TaskListMachineEventTypes.NEXT_PAGE]: {
              actions: ["persistNextPage"],
              target: "fetchForTasks",
            },
            [TaskListMachineEventTypes.CHANGE_ROWS_PER_PAGE]: {
              actions: ["persistRowPerPage"],
              target: "fetchForTasks",
            },
            [TaskListMachineEventTypes.SEND_SELECTION_TO_PARENT]: {
              actions: ["selectTask"],
            },
          },
        },
      },
    },
    {
      services,
      actions: actions as any,
    },
  );
