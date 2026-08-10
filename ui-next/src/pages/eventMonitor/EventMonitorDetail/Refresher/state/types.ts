import {
  EventExecutionResult,
  GroupedEventItem,
} from "pages/eventMonitor/types";
import { AuthHeaders } from "types/common";

export enum PageType {
  EVENT_LISTING = "EVENT_LISTING",
  EVENT_DETAIL = "EVENT_DETAIL",
}

export interface RefreshMachineContext {
  elapsed: number;
  duration: number;
  durationSet: number;
  authHeaders?: AuthHeaders;
  eventData?: GroupedEventItem[];
  eventName?: string;
  timeRange?: number;
  pageType?: PageType;
  eventListData?: EventExecutionResult;
}

export enum RefreshMachineStates {
  INIT = "INIT",
  RUNNING = "RUNNING",
  END_TIMER = "END_TIMER",
  FETCH_DATA = "FETCH_DATA",
  ERROR = "ERROR",
  FETCH_EVENT_LIST_DATA = "FETCH_EVENT_LIST_DATA",
}

export enum RefreshMachineEventTypes {
  TICK = "TICK",
  UPDATE_DURATION = "UPDATE_DURATION",
  REFRESH = "REFRESH",
  PERSIST_EVENT_NAME_AND_TIME = "PERSIST_EVENT_NAME_AND_TIME",
}

export type TickEvent = {
  type: RefreshMachineEventTypes.TICK;
};

export type RefreshEvent = {
  type: RefreshMachineEventTypes.REFRESH;
};

export type UpdateDurationEvent = {
  type: RefreshMachineEventTypes.UPDATE_DURATION;
  value: number;
};

export type PersistEventNameAndDuration = {
  type: RefreshMachineEventTypes.PERSIST_EVENT_NAME_AND_TIME;
  data: {
    eventName: string;
    timeRange: number;
  };
};

export type TimerEvents =
  | TickEvent
  | UpdateDurationEvent
  | RefreshEvent
  | PersistEventNameAndDuration;
