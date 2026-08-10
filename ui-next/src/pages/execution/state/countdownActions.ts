import { assign, sendParent } from "xstate";
import {
  COUNT_DOWN_TYPE,
  ExecutionActionTypes,
  CountdownContext,
  UpdateDurationEvent,
} from "./types";

export const updateCountdownDuration = assign<
  CountdownContext,
  UpdateDurationEvent
>({
  duration: (ctx: CountdownContext, event) => event?.duration || 30,
});

export const resetCountdownElapsed = assign({
  elapsed: 0,
});

export const updateCountdownType = (type: COUNT_DOWN_TYPE) =>
  assign({
    countdownType: type,
  });

export const updateParentDuration = sendParent(
  (_ctx: CountdownContext, event: any) => ({
    type: ExecutionActionTypes.UPDATE_DURATION,
    duration: event?.duration,
    countdownType: event?.countdownType,
  }),
);

export const updateParentIsDisabled = (isDisabled = false) =>
  sendParent((ctx: CountdownContext) => ({
    type: ExecutionActionTypes.UPDATE_DURATION,
    duration: ctx?.duration,
    countdownType: ctx?.countdownType,
    isDisabled: isDisabled,
  }));
