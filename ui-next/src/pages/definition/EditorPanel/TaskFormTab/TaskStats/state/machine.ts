import { createMachine } from "xstate";
import {
  TaskStatsMachineContext,
  TaskStatsEventTypes,
  TaskStatsEvents,
} from "./types";
import * as services from "./services";
import * as actions from "./actions";
import * as guards from "./guards";

import { FEATURES, featureFlags } from "utils/flags";

const taskStatsEnabled = featureFlags.isEnabled(FEATURES.DISABLE_TASK_STATS);

export const taskStatsMachine = createMachine<
  TaskStatsMachineContext,
  TaskStatsEvents
>(
  {
    id: "taskStatsMachine",
    predictableActionArguments: true,
    initial: "init",
    context: {
      // Context will be initialized by parent machine
      completedRateSeries: [],
      failedRateSeries: [],
      completedAmount: 0,
      failedAmount: 0,
      startHoursBack: 0,
      scheduledAmount: 0,
      authHeaders: undefined,
      taskName: "",
    },
    states: {
      notEnabled: {},
      init: {
        always: [
          { cond: () => taskStatsEnabled, target: "fetchForStats" },
          { target: "notEnabled" },
        ],
      },
      fetchForStats: {
        invoke: {
          src: "fetchForTaskMetrics",
          onDone: {
            actions: "persistMetrics",
            target: "idle",
          },
          onError: {
            target: "noStatsAvailable",
          },
        },
      },
      noStatsAvailable: {
        on: {
          [TaskStatsEventTypes.UPDATE_TASK_NAME]: {
            actions: ["persistTaskName"],
            target: "waitForName",
            cond: "nameChanged",
          },
        },
      },
      idle: {
        on: {
          [TaskStatsEventTypes.CHANGE_START_TIME]: {
            actions: ["persistStartTimeStamp"],
            target: "fetchForStats",
          },
          [TaskStatsEventTypes.UPDATE_TASK_NAME]: {
            actions: ["persistTaskName"],
            target: "waitForName",
            cond: "nameChanged",
          },
        },
      },
      waitForName: {
        after: {
          800: "fetchForStats",
        },
        on: {
          [TaskStatsEventTypes.UPDATE_TASK_NAME]: {
            actions: ["persistTaskName"],
            target: "waitForName",
          },
        },
      },
    },
  },
  {
    services,
    actions: actions as any,
    guards: guards as any,
  },
);
