import { createMachine } from "xstate";
import {
  RefreshMachineContext,
  RefreshMachineEventTypes,
  TimerEvents,
} from "./types";
import * as actions from "./actions";
import * as guards from "./guards";

export const timerMachine = createMachine<RefreshMachineContext, TimerEvents>(
  {
    id: "timerMachine",
    initial: "running",
    context: {
      durationSet: 60,
      elapsed: 0,
      duration: 60,
    },
    states: {
      running: {
        invoke: {
          src: (_context) => (cb) => {
            const interval = setInterval(() => {
              cb(RefreshMachineEventTypes.TICK);
            }, 1000);

            return () => {
              clearInterval(interval);
            };
          },
        },
        always: {
          target: "endTimer",
          cond: "elapsedIsBiggerThanDuration",
        },
        on: {
          TICK: {
            actions: "persistElapsed",
          },
        },
      },
      endTimer: {
        entry: ["sendRefresh", "restartTimer"],
        always: "running",
      },
    },
    on: {
      [RefreshMachineEventTypes.UPDATE_DURATION]: {
        actions: ["persistDuration", "restartTimer"],
      },
      [RefreshMachineEventTypes.REFRESH]: {
        actions: ["restartTimer"],
      },
    },
  },
  {
    actions: actions as any,
    guards: guards as any,
  },
);
