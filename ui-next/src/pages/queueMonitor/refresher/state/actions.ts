import { assign, sendParent } from "xstate";
import {
  RefreshMachineContext,
  UpdateDurationEvent,
  RefreshMachineEventTypes,
} from "./types";

export const persistDuration = assign<
  RefreshMachineContext,
  UpdateDurationEvent
>({
  duration: (_, event) => event.value,
  durationSet: (_, event) => event.value,
});

export const persistElapsed = assign<RefreshMachineContext>({
  elapsed: (context) => context.elapsed + 1,
});

export const sendRefresh = sendParent(() => ({
  type: RefreshMachineEventTypes.REFRESH,
}));

export const forwardToParent = sendParent((__context, event) => event);

export const restartTimer = assign<RefreshMachineContext>({
  duration: ({ durationSet }) => durationSet,
  elapsed: 0,
});
