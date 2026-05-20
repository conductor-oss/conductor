import { createMachine } from "xstate";
import {
  RightPanelContext,
  RightPanelContextEventTypes,
  RightPanelStates,
  RightPanelEvents,
} from "./types";

import * as services from "./services";
import * as actions from "./actions";
import * as guards from "./guards";
import { ExecutionActionTypes } from "pages/execution/state/types";
import { SUMMARY_TAB } from "pages/execution/state/constants";

export const rightPanelMachine = createMachine<
  RightPanelContext,
  RightPanelEvents
>(
  {
    id: "rightPanelMachine",
    predictableActionArguments: true,
    initial: "init",
    context: {
      selectedTask: undefined,
      authHeaders: undefined,
      taskLogs: undefined,
      currentTab: SUMMARY_TAB,
      executionStatusMap: undefined,
    },
    states: {
      init: {
        type: "parallel",
        states: {
          main: {
            initial: RightPanelStates.IDLE,
            states: {
              [RightPanelStates.IDLE]: {
                on: {
                  [RightPanelContextEventTypes.SET_SELECTED_TASK]: {
                    actions: [
                      "persistSelectedTask",
                      "sendSelectedTaskToParent",
                    ],
                  },
                  [RightPanelContextEventTypes.CLOSE_RIGHT_PANEL]: {
                    target: RightPanelStates.END,
                  },
                  [RightPanelContextEventTypes.SET_UPDATED_EXECUTION]: {
                    actions: [
                      "extractUpdates",
                      "notifySelectedTaskUpdateToParent",
                    ],
                  },
                  [RightPanelContextEventTypes.RE_RUN_WORKFLOW_FROM_TASK]: {
                    target: RightPanelStates.RERUN_WORKFLOW_FROM_TASK,
                  },
                  [RightPanelContextEventTypes.CLEAR_ERROR_MESSAGE]: {
                    actions: ["clearErrorMessage"],
                  },
                  [RightPanelContextEventTypes.SET_DO_WHILE_ITERATION]: {
                    actions: ["sendDoWhileIterationToParent"],
                  },
                },
              },

              [RightPanelStates.RERUN_WORKFLOW_FROM_TASK]: {
                invoke: {
                  id: "reRunWoflowFromTaskService",
                  src: "reRunWoflowFromTask",
                  onDone: {
                    actions: ["notifyTaskUpdateToParent"],
                    target: RightPanelStates.IDLE,
                  },
                  onError: {
                    actions: ["persistError"],
                    target: RightPanelStates.IDLE,
                  },
                },
              },
              [RightPanelStates.END]: {
                always: {
                  target: "#rightPanelMachine.final",
                },
              },
            },
          },
          [RightPanelStates.DETAILED_SECTION]: {
            initial: RightPanelStates.SUMMARY,
            on: {
              [RightPanelContextEventTypes.CHANGE_CURRENT_TAB]: {
                target: ".addressChangeTab",
                actions: ["updateCurrentTab"],
              },
            },
            states: {
              addressChangeTab: {
                always: [
                  {
                    target: RightPanelStates.SUMMARY,
                    cond: "isSummaryTab",
                  },
                  {
                    target: RightPanelStates.INPUT,
                    cond: "isInputTab",
                  },
                  {
                    target: RightPanelStates.OUTPUT,
                    cond: "isOutputTab",
                  },
                  {
                    target: RightPanelStates.LOGS,
                    cond: "isLogsTab",
                  },
                  {
                    target: RightPanelStates.JSON,
                    cond: "isJsonTab",
                  },
                  { target: RightPanelStates.DEFINITION },
                ],
              },
              [RightPanelStates.SUMMARY]: {
                initial: "summaryIdle",
                on: {
                  [RightPanelContextEventTypes.UPDATE_SELECTED_TASK_STATUS]: {
                    target: `.${RightPanelStates.UPDATE_TASK_STATUS}`,
                    cond: "isSelectedTaskStatusUpdatable",
                  },
                },
                states: {
                  summaryIdle: {},
                  [RightPanelStates.UPDATE_TASK_STATUS]: {
                    invoke: {
                      id: "changeTaskStatus",
                      src: "updateTaskState",
                      onDone: {
                        actions: ["notifyTaskUpdateToParent"],
                        target: "summaryIdle",
                      },
                      onError: {
                        actions: ["persistError"],
                        target: "summaryIdle",
                      },
                    },
                  },
                },
              },
              [RightPanelStates.INPUT]: {},
              [RightPanelStates.OUTPUT]: {},
              [RightPanelStates.LOGS]: {
                initial: RightPanelStates.FETCH_SELECTED_TASK_LOGS,
                on: {
                  [ExecutionActionTypes.FETCH_FOR_LOGS]: {
                    target: `.${RightPanelStates.FETCH_SELECTED_TASK_LOGS}`,
                  },
                  [RightPanelContextEventTypes.SET_SELECTED_TASK]: {
                    target: `.${RightPanelStates.FETCH_AFTER}`,
                  },
                },
                states: {
                  logsIdle: {},
                  [RightPanelStates.FETCH_SELECTED_TASK_LOGS]: {
                    invoke: {
                      id: "getTaskLogs",
                      src: "fetchTaskLogs",
                      onDone: {
                        actions: ["updateTaskLogs"],
                        target: "logsIdle",
                      },
                    },
                  },
                  [RightPanelStates.FETCH_AFTER]: {
                    after: {
                      300: {
                        target: RightPanelStates.FETCH_SELECTED_TASK_LOGS,
                      },
                    },
                  },
                },
              },
              [RightPanelStates.JSON]: {},
              [RightPanelStates.DEFINITION]: {},
            },
          },
        },
      },
      final: {
        type: "final",
      },
    },
  },
  {
    services,
    actions: actions as any,
    guards: guards as any,
  },
);
