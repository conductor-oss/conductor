import { assign, createMachine } from "xstate";
import { COUNT_DOWN_TYPE } from "pages/execution/state/types";
import {
  updateParentIsDisabled,
  resetCountdownElapsed,
  updateCountdownDuration,
  updateCountdownType,
  updateParentDuration,
} from "pages/execution/state/countdownActions";
import {
  CountdownEventTypes,
  CountdownContext,
  CountdownEvents,
} from "./types";

const actions = {
  resetCountdownElapsed,
  updateCountdownDuration,
  updateCountdownType,
  updateParentDuration,
  updateInfinityCountdownType: updateCountdownType(COUNT_DOWN_TYPE.INFINITE),
  refreshImmediatelyWhileDisabled: updateParentIsDisabled(true),
  enableCountdown: updateParentIsDisabled(false),
} as any;

export const countdownMachine = createMachine<
  CountdownContext,
  CountdownEvents
>(
  {
    id: "countdownMachine",
    context: {
      duration: 30,
      elapsed: 0,
      executionStatus: "",
      countdownType: COUNT_DOWN_TYPE.INFINITE,
      isDisabled: false,
    },
    initial: "running",
    states: {
      running: {
        invoke: {
          src: (_context) => (sp) => {
            const interval = setInterval(() => {
              sp(CountdownEventTypes.TICK);
            }, 1000);
            return () => {
              clearInterval(interval);
            };
          },
        },
        always: [
          {
            target: "disabled",
            cond: (ctx: CountdownContext) => !!ctx?.isDisabled,
          },
          {
            target: "finish",
            cond: (ctx: CountdownContext) => ctx?.elapsed >= ctx?.duration,
          },
        ],
        on: {
          [CountdownEventTypes.TICK]: {
            actions: assign({
              elapsed: (ctx: any) => ctx.elapsed + 1,
            }) as any,
          },
          [CountdownEventTypes.DISABLE]: {
            actions: ["resetCountdownElapsed"],
            target: "disabled",
          },
          [CountdownEventTypes.FORCE_FINISH]: {
            actions: ["refreshImmediately"],
            target: "finish",
          },
          [CountdownEventTypes.UPDATE_DURATION]: {
            actions: [
              "updateParentDuration",
              "updateCountdownDuration",
              "resetCountdownElapsed",
            ],
          },
        },
      },
      disabled: {
        on: {
          [CountdownEventTypes.ENABLE]: {
            actions: [
              assign({
                isDisabled: false,
              }) as any,
              "enableCountdown",
            ],
            target: "running",
          },
          [CountdownEventTypes.FORCE_FINISH]: {
            actions: ["refreshImmediatelyWhileDisabled"],
            target: "finish",
          },
        },
      },
      idle: {
        on: {
          [CountdownEventTypes.UPDATE_DURATION]: {
            actions: [
              "updateParentDuration",
              "updateCountdownDuration",
              "resetCountdownElapsed",
              "updateInfinityCountdownType",
            ],
            target: "running",
          },
          [CountdownEventTypes.ENABLE]: {
            actions: ["updateInfinityCountdownType", "enableCountdown"],
            target: "running",
          },
          [CountdownEventTypes.DISABLE]: {
            actions: ["updateInfinityCountdownType"],
            target: "disabled",
          },
          [CountdownEventTypes.FORCE_FINISH]: {
            actions: ["refreshImmediately"],
            target: "finish",
          },
        },
      },
      finish: {
        type: "final",
      },
    },
  },
  {
    actions,
  },
);
