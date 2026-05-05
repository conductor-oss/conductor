import { createMachine } from "xstate";
import {
  PageType,
  RefreshMachineContext,
  RefreshMachineEventTypes,
  RefreshMachineStates,
  TimerEvents,
} from "./types";
import * as actions from "./actions";
import * as services from "./services";

export const refreshMachine = createMachine<RefreshMachineContext, TimerEvents>(
  {
    id: "refreshMachine",
    predictableActionArguments: true,
    initial: RefreshMachineStates.INIT,
    context: {
      durationSet: 60,
      elapsed: 0,
      duration: 60,
      pageType: undefined,
    },
    on: {
      [RefreshMachineEventTypes.UPDATE_DURATION]: {
        actions: ["persistDuration", "restartTimer"],
      },
      [RefreshMachineEventTypes.REFRESH]: {
        actions: ["restartTimer"],
        target: RefreshMachineStates.FETCH_DATA,
      },
      [RefreshMachineEventTypes.PERSIST_EVENT_NAME_AND_TIME]: {
        actions: ["persistEventNameAndTimer"],
        target: RefreshMachineStates.FETCH_DATA,
      },
    },
    states: {
      [RefreshMachineStates.INIT]: {
        entry: "persistLocalStorageDuration",
        always: [
          {
            cond: (ctx) => !ctx.eventData?.length,
            target: RefreshMachineStates.FETCH_DATA,
          },

          {
            target: RefreshMachineStates.RUNNING,
          },
        ],
      },
      [RefreshMachineStates.FETCH_DATA]: {
        invoke: {
          src: "fetchEventData",
          onDone: [
            {
              cond: (context) => context.pageType === PageType.EVENT_LISTING,
              actions: ["persistEventListData", "restartTimer"],
              target: RefreshMachineStates.RUNNING,
            },
            {
              actions: ["persistEventData", "restartTimer"],
              target: RefreshMachineStates.RUNNING,
            },
          ],
          onError: {
            actions: "showErrorMessage",
            target: RefreshMachineStates.ERROR,
          },
        },
      },
      [RefreshMachineStates.RUNNING]: {
        on: {
          [RefreshMachineEventTypes.TICK]: {
            actions: "persistElapsed",
          },
        },
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
          target: RefreshMachineStates.FETCH_DATA,
          cond: (context: RefreshMachineContext) =>
            context.elapsed >= context.duration,
        },
      },
      [RefreshMachineStates.ERROR]: {},
    },
  },
  {
    actions: actions as any,
    services: services as any,
  },
);
