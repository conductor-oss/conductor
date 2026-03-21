import { createMachine } from "xstate";
import { timerMachine } from "../refresher/state/machine";
import { RefreshMachineEventTypes } from "../refresher/state/types";
import * as actions from "./actions";
import * as guards from "./guards";
import * as services from "./service";
import {
  QueueMachineEventTypes,
  QueueMonitorMachineContext,
  QueueMonitorMachineEvents,
} from "./types";

export const queueMonitorMachine = createMachine<
  QueueMonitorMachineContext,
  QueueMonitorMachineEvents
>(
  {
    id: "queueMachine",
    predictableActionArguments: true,
    initial: "idle",
    context: {
      pollDataByQueueName: {},
      queueData: {},
      authHeaders: undefined,
      refetchDuration: 60,
      filterOptions: {
        queue: undefined,
        worker: undefined,
        lastPollTime: undefined,
      },
      filterOptionsToApply: {
        queue: undefined,
        worker: undefined,
        lastPollTime: undefined,
      },
      errorMessage: "",
    },
    on: {
      [QueueMachineEventTypes.FETCH_TASKS_QUEUE]: {
        actions: "persistFetchRequestParams",
        target: "fetchForTaskPolls",
      },
      [RefreshMachineEventTypes.UPDATE_DURATION]: {
        actions: ["persistDuration"],
        target: "updateDurationDuringRefresh",
      },
    },
    states: {
      idle: {},
      showError: {},
      fetchForTaskPolls: {
        invoke: {
          src: "fetchForPollData",
          onDone: {
            actions: "persistPollQueueData",
            target: "checkRefreshConfig",
          },
          onError: {
            actions: "peristErrorMessage",
            target: "showError",
          },
        },
      },
      checkRefreshConfig: {
        invoke: {
          src: "maybePullOrderAndVisibility",
          onDone: {
            actions: ["persistLocalStorageDuration"],
            target: "ready",
          },
        },
      },
      updateDurationDuringRefresh: {
        invoke: {
          src: "saveOrderAndVisibility",
          onDone: "checkRefreshConfig",
        },
      },
      ready: {
        on: {
          [QueueMachineEventTypes.UPDATE_QUEUE_OPTION]: {
            actions: "persistQueueOption",
          },
          [QueueMachineEventTypes.UPDATE_WORKER_COUNT_OPTION]: {
            actions: "persistWorkerOption",
          },
          [QueueMachineEventTypes.UPDATE_LAST_POLL_TIME_OPTION]: {
            actions: "persistLastPollTimeOption",
          },
        },
        type: "parallel",
        states: {
          tableSelection: {
            initial: "checkSelection",
            states: {
              checkSelection: {
                always: [
                  { cond: "noQueueNameSelected", target: "noSelection" },
                  {
                    target: "withSelection",
                  },
                ],
              },
              noSelection: {
                on: {
                  [QueueMachineEventTypes.SELECT_QUEUE_NAME]: {
                    actions: "persistQueueSelection",
                    target: "withSelection",
                  },
                },
              },
              withSelection: {
                on: {
                  [QueueMachineEventTypes.SELECT_QUEUE_NAME]: {
                    actions: "persistQueueSelection",
                    target: "withSelection",
                  },
                },
              },
            },
          },
          refresher: {
            invoke: {
              src: timerMachine,
              id: "refreshMachine",
              data: ({ refetchDuration }) => ({
                durationSet: refetchDuration,
                elapsed: 0,
                duration: refetchDuration,
              }),
            },
            initial: "timer",
            states: {
              fetchForTaskPolls: {
                invoke: {
                  src: "fetchForPollData",
                  onDone: {
                    actions: "persistPollQueueData",
                    target: "timer",
                  },
                },
              },
              timer: {
                on: {
                  [RefreshMachineEventTypes.REFRESH]: {
                    target: "fetchForTaskPolls",
                    actions: ["forwardToRefreshMachine"],
                  },
                  [RefreshMachineEventTypes.UPDATE_DURATION]: {
                    actions: ["persistDuration", "forwardToRefreshMachine"],
                    target: "updateDuration",
                  },
                },
              },
              updateDuration: {
                invoke: {
                  src: "saveOrderAndVisibility",
                  onDone: "timer",
                },
              },
            },
          },
          filterDialog: {
            initial: "closeDialog",
            states: {
              closeDialog: {
                on: { openDialog: "openDialog" },
              },
              openDialog: {
                on: { closeDialog: "closeDialog" },
              },
            },
          },
        },
      },
    },
  },
  {
    actions: actions as any,
    services,
    guards: guards as any,
  },
);
