import { assign, DoneInvokeEvent } from "xstate";
import {
  PersistEventNameAndDuration,
  RefreshMachineContext,
  UpdateDurationEvent,
} from "./types";
export const LOCAL_STORAGE_KEY = "eventMonitorRefreshSeconds";

const getStoredDuration = () =>
  parseInt(localStorage.getItem(LOCAL_STORAGE_KEY) || "60", 10);

export const persistLocalStorageDuration = assign(() => {
  const storedDuration = getStoredDuration();
  return { durationSet: storedDuration, duration: storedDuration };
});

export const persistDuration = assign<
  RefreshMachineContext,
  UpdateDurationEvent
>({
  duration: (_, event) => {
    const duration = event.value;
    localStorage.setItem(LOCAL_STORAGE_KEY, `${duration}`);

    return duration;
  },
  durationSet: (_, event) => event.value,
});

export const persistElapsed = assign<RefreshMachineContext>({
  elapsed: (context) => context.elapsed + 1,
});

export const restartTimer = assign<RefreshMachineContext>({
  duration: ({ durationSet }) => durationSet,
  elapsed: 0,
});

export const persistEventData = assign<
  RefreshMachineContext,
  DoneInvokeEvent<any>
>({
  eventData: (_, event) => event.data,
});

export const persistEventListData = assign<
  RefreshMachineContext,
  DoneInvokeEvent<any>
>({
  eventListData: (_, event) => event.data,
});

export const persistEventNameAndTimer = assign<
  RefreshMachineContext,
  PersistEventNameAndDuration
>({
  eventName: (_, { data }) => data.eventName,
  timeRange: (_, { data }) => data.timeRange,
});
