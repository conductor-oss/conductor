import { createMachine } from "xstate";
import * as actions from "./actions";
import * as guards from "./guards";
import * as services from "./services";
import { FlowActionTypes } from "components/features/flow/state/types";
import {
  ExecutionActionTypes,
  ExecutionMachineEvents,
  ExecutionMachineContext,
  ExecutionTabs,
  COUNT_DOWN_TYPE,
} from "./types";
import { taskListMachine } from "../TaskList";
import {
  RightPanelContextEventTypes,
  SetSelectedTaskEvent,
  rightPanelMachine,
} from "../RightPanel";
import { SUMMARY_TAB } from "./constants";

import { countdownMachine } from "./countdownMachine";

export const executionMachine = createMachine<
  ExecutionMachineContext,
  ExecutionMachineEvents
>(
  {
    id: "executionDefintionMachine",
    initial: "idle",
    predictableActionArguments: true,
    context: {
      execution: undefined,
      executionId: undefined,
      flowChild: undefined,
      error: undefined,
      expandedDynamic: [],
      authHeaders: undefined,
      currentTab: ExecutionTabs.DIAGRAM_TAB,
      duration: 30,
      countdownType: COUNT_DOWN_TYPE.INFINITE,
      isDisabledCountdown: false,
      executionStatusMap: undefined,
      isAssistantPanelOpen: false,
    },
    on: {
      [ExecutionActionTypes.UPDATE_EXECUTION]: {
        actions: ["persistExecutionId"],
        target: "fetchForExecution",
      },
      [ExecutionActionTypes.REPORT_FLOW_ERROR]: {
        actions: ["persistFlowError"],
      },
    },
    states: {
      idle: {
        entry: "instanciateFlow",
      },
      fetchForExecution: {
        // Initial fetch by url
        invoke: {
          id: "executionFetcher",
          src: "fetchExecution",
          onDone: {
            actions: ["updateExecution", "notifyOnHumanTask"],
            target: "init",
          },
          onError: [
            {
              cond: "isUseGlobalMessage",
              actions: "setErrorMessage",
              target: "noAccess",
            },
            {
              actions: ["logError", "assignError"],
              target: "init",
            },
          ],
        },
      },
      noAccess: {
        type: "final",
      },
      init: {
        type: "parallel",
        states: {
          rightPanel: {
            initial: "closed",
            states: {
              opened: {
                on: {
                  [FlowActionTypes.SELECT_NODE_EVT]: {
                    actions: ["nodeToTaskSelectionToPanel", "setQueryParam"],
                  },
                  [FlowActionTypes.SELECT_TASK_WITH_TASK_REF]: {
                    actions: ["taskToTaskSelectionToPanel"],
                  },
                  [ExecutionActionTypes.UPDATE_TASKID_IN_URL]: {
                    actions: ["setTaskIdQueryParam"],
                  },
                  [RightPanelContextEventTypes.SET_SELECTED_TASK]: {
                    actions: ["forwardSelectionToPanel"],
                  },
                  [ExecutionActionTypes.FETCH_FOR_LOGS]: {
                    actions: ["forwardSelectionToPanel"],
                  },
                  [ExecutionActionTypes.CLOSE_RIGHT_PANEL]: {
                    target: "closed",
                  },
                  [ExecutionActionTypes.EXECUTION_UPDATED]: {
                    actions: ["sendUpdatedExecution"],
                  },
                  [ExecutionActionTypes.SET_DO_WHILE_ITERATION]: {
                    actions: [
                      "persistDoWhileIteration",
                      "updateWorkflowDefinition",
                      "notifyFlowUpdates",
                    ],
                  },
                  [ExecutionActionTypes.UPDATE_SELECTED_TASK]: {
                    actions: [
                      "updateSelectedTask",
                      "updateExecutionMap",
                      "notifyFlowUpdates",
                    ],
                  },
                  [ExecutionActionTypes.UPDATE_QUERY_PARAM]: {
                    actions: ["updateQueryParam"],
                  },
                  [ExecutionActionTypes.TOGGLE_ASSISTANT_PANEL]: {
                    actions: ["toggleAssistantPanel"],
                    target: "closed",
                  },
                },
                invoke: {
                  src: rightPanelMachine,
                  id: "rightPanelMachine",
                  data: {
                    selectedTask: (
                      __context: ExecutionMachineContext,
                      event: SetSelectedTaskEvent,
                    ) => {
                      return event.selectedTask;
                    },
                    authHeaders: ({ authHeaders }: ExecutionMachineContext) =>
                      authHeaders,
                    executionId: ({ executionId }: ExecutionMachineContext) =>
                      executionId,
                    executionStatusMap: ({
                      executionStatusMap,
                    }: ExecutionMachineContext) => executionStatusMap,
                    currentTab: SUMMARY_TAB,
                  },
                  onDone: {
                    actions: [
                      "cleanSelection",
                      "selectNodeInFlow",
                      "gtagEventLogger",
                      "clearQueryParams",
                    ],
                    target: "closed",
                  },
                },
              },
              closed: {
                on: {
                  [RightPanelContextEventTypes.SET_SELECTED_TASK]: {
                    actions: ["closeAssistantPanel"],
                    target: "opened",
                  },
                  [FlowActionTypes.SELECT_NODE_EVT]: {
                    actions: [
                      "nodeToTaskSelectionToPanel",
                      "setQueryParam",
                      "closeAssistantPanel",
                    ],
                    target: "opened",
                  },
                  [FlowActionTypes.SELECT_TASK_WITH_TASK_REF]: {
                    actions: [
                      "taskToTaskSelectionToPanel",
                      "closeAssistantPanel",
                    ],
                    target: "opened",
                  },
                  [ExecutionActionTypes.UPDATE_QUERY_PARAM]: {
                    actions: ["updateQueryParam"],
                  },
                  [ExecutionActionTypes.TOGGLE_ASSISTANT_PANEL]: {
                    actions: ["toggleAssistantPanel"],
                  },
                },
              },
            },
          },
          detailSelection: {
            initial: "initDiagram",
            on: {
              [ExecutionActionTypes.CHANGE_EXECUTION_TAB]: {
                target: ".addressChangeTab",
                actions: ["persistCurrentTab", "gtagEventLogger"],
              },
            },
            states: {
              initDiagram: {
                always: [
                  {
                    cond: (ctx) =>
                      !!ctx.selectedTaskReferenceName || !!ctx.selectedTaskId,
                    actions: ["notifyFlowUpdates", "delayedNodeSelection"],
                    target: "diagram",
                  },
                  {
                    target: "diagram",
                  },
                ],
              },
              addressChangeTab: {
                always: [
                  {
                    target: "taskList",
                    cond: "isTaskListTab",
                  },
                  {
                    target: "timeLine",
                    cond: "isTimeLineTab",
                  },
                  {
                    target: "workflowInputOutput",
                    cond: "isTimeWorkflowInputOutputTab",
                  },
                  {
                    target: "json",
                    cond: "isJsonTab",
                  },
                  {
                    target: "summary",
                    cond: "isSummaryTab",
                  },
                  { target: "diagram" },
                ],
              },
              diagram: {
                entry: "notifyFlowUpdates",
                on: {
                  [ExecutionActionTypes.EXPAND_DYNAMIC_TASK]: {
                    actions: [
                      "addToExpandedDynamic",
                      "updateWorkflowDefinition",
                      "notifyFlowUpdates",
                      "startRenderingGtag",
                    ],
                  },
                  [ExecutionActionTypes.COLLAPSE_DYNAMIC_TASK]: {
                    actions: [
                      "removeFromExpandedDynamic",
                      "updateWorkflowDefinition",
                      "notifyFlowUpdates",
                      "startRenderingGtag",
                    ],
                  },
                },
              },
              taskList: {
                invoke: {
                  src: taskListMachine,
                  id: "taskListMachine",
                  data: {
                    authHeaders: ({ authHeaders }: ExecutionMachineContext) =>
                      authHeaders,
                    executionId: ({ executionId }: ExecutionMachineContext) =>
                      executionId,
                    startIndex: 0,
                    rowsPerPage: 20,
                  },
                  onDone: {},
                },
              },
              timeLine: {},
              summary: {},
              workflowInputOutput: {},
              json: {},
            },
          },
          executionActions: {
            initial: "determineExecutionCurrentState",
            on: {
              [ExecutionActionTypes.REFETCH]: {
                target: ".fetchForExecution",
                actions: ["gtagEventLogger"],
              },
              [ExecutionActionTypes.CLEAR_ERROR]: {
                actions: ["clearError", "gtagEventLogger"],
              },
              [ExecutionActionTypes.UPDATE_DURATION]: {
                actions: ["updateExecutionDuration", "gtagEventLogger"],
              },
            },
            states: {
              fetchForExecution: {
                invoke: {
                  id: "executionFetcher",
                  src: "fetchExecution",
                  onDone: {
                    actions: [
                      "updateExecution",
                      "notifyFlowUpdates",
                      "startRenderingGtag",
                      "raiseExecutionUpdated",
                      "notifyOnHumanTask",
                    ],
                    target: "determineExecutionCurrentState",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                    target: "determineExecutionCurrentState",
                  },
                },
              },
              delayFetchForExecution: {
                after: {
                  1000: {
                    target: "fetchForExecution",
                  },
                },
              },
              determineExecutionCurrentState: {
                // states should rely on the EXECUTION status
                always: [
                  {
                    target: "finishedExecution.terminated",
                    cond: "isExecutionTerminated",
                  },
                  {
                    target: "finishedExecution.failed",
                    cond: "isExecutionFailed",
                  },
                  {
                    target: "finishedExecution.timedOut",
                    cond: "isExecutionTimedOut",
                  },
                  {
                    target: "finishedExecution.paused",
                    cond: "isExecutionPaused",
                  },
                  {
                    target: "finishedExecution.completed",
                    cond: "isExecutionCompleted",
                  },
                  {
                    target: "runningExecution",
                  },
                ],
              },
              terminateExecution: {
                invoke: {
                  id: "terminateExecutionService",
                  src: "terminateExecution",
                  onDone: {
                    target: "delayFetchForExecution",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                    target: "fetchForExecution",
                  },
                },
              },
              pauseExecution: {
                invoke: {
                  id: "pauseExecutionService",
                  src: "pauseExecution",
                  onDone: {
                    target: "delayFetchForExecution",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                    target: "fetchForExecution",
                  },
                },
              },
              resumeExecution: {
                invoke: {
                  id: "resumeExecutionService",
                  src: "resumeExecution",
                  onDone: {
                    target: "delayFetchForExecution",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                    target: "fetchForExecution",
                  },
                },
              },
              restartExecution: {
                invoke: {
                  id: "restartExecutionService",
                  src: "restartExecution",
                  onDone: {
                    target: "delayFetchForExecution",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                    target: "fetchForExecution",
                  },
                },
              },
              retryExecution: {
                invoke: {
                  id: "retryExecutionService",
                  src: "retryExecution",
                  onDone: {
                    target: "delayFetchForExecution",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                    target: "fetchForExecution",
                  },
                },
              },
              runningExecution: {
                invoke: {
                  id: "countdownMachine",
                  src: countdownMachine,
                  data: {
                    duration: (context: ExecutionMachineContext) =>
                      context.duration,
                    elapsed: 0,
                    executionStatus: "",
                    countdownType: (context: ExecutionMachineContext) =>
                      context.countdownType,
                    isDisabled: (context: ExecutionMachineContext) =>
                      context.isDisabledCountdown,
                  },
                  onDone: {
                    actions: ["fetchForLogs"],
                    target: "fetchForExecution",
                  },
                },
                on: {
                  [ExecutionActionTypes.TERMINATE_EXECUTION]: {
                    target: "terminateExecution",
                    actions: ["gtagEventLogger"],
                  },
                  [ExecutionActionTypes.PAUSE_EXECUTION]: {
                    target: "pauseExecution",
                    actions: ["gtagEventLogger"],
                  },
                  [ExecutionActionTypes.UPDATE_VARIABLES]: {
                    target: "updateVariablesOfExecution",
                  },
                },
              },
              updateVariablesOfExecution: {
                invoke: {
                  id: "updateVariablesService",
                  src: "updateVariables",
                  onDone: {
                    actions: ["persistSuccessUpdateVariablesMessage"],
                    target: "delayFetchForExecution",
                  },
                  onError: {
                    actions: ["logError", "assignError", "gtagErrorLogger"],
                  },
                },
              },
              finishedExecution: {
                initial: "completed",
                states: {
                  completed: {
                    on: {
                      [ExecutionActionTypes.RESTART_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.restartExecution",
                      },
                    },
                  },
                  terminated: {
                    on: {
                      [ExecutionActionTypes.RESTART_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.restartExecution",
                      },
                      [ExecutionActionTypes.RETRY_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.retryExecution",
                      },
                    },
                  },
                  failed: {
                    on: {
                      [ExecutionActionTypes.RESTART_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.restartExecution",
                      },
                      [ExecutionActionTypes.RETRY_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.retryExecution",
                      },
                      [ExecutionActionTypes.TERMINATE_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.terminateExecution",
                      },
                    },
                  },
                  timedOut: {
                    on: {
                      [ExecutionActionTypes.RESTART_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.restartExecution",
                      },
                      [ExecutionActionTypes.RETRY_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.retryExecution",
                      },

                      [ExecutionActionTypes.TERMINATE_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.terminateExecution",
                      },
                    },
                  },
                  paused: {
                    on: {
                      [ExecutionActionTypes.RESUME_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.resumeExecution",
                      },
                      [ExecutionActionTypes.TERMINATE_EXECUTION]: {
                        target:
                          "#executionDefintionMachine.init.executionActions.terminateExecution",
                      },
                    },
                  },
                },
              },
            },
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    guards: guards as any,
    services: services as any,
  },
);
