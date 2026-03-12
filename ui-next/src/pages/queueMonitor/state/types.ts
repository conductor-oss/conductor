import { DoneInvokeEvent } from "xstate";
import { AuthHeaders } from "types/common";
import { RefreshEvent, UpdateDurationEvent } from "../refresher/state/types";

export type QueueSizeCount = {
  size: number;
  pollerCount?: number;
};

export type QueueData = Record<string, QueueSizeCount>;

export interface PollData {
  queueName: string;
  domain: string;
  workerId: string;
  lastPollTime: number;
}

export enum RangeOptions {
  GT = "GT",
  LT = "LT",
}

export type FilterOption = {
  size: number;
  option: RangeOptions;
};

export interface FilterOptions {
  queue?: FilterOption;
  worker?: FilterOption;
  lastPollTime?: FilterOption;
}

export interface QueueMonitorMachineContext {
  authHeaders?: AuthHeaders;
  pollDataByQueueName?: Record<string, PollData[]>;
  selectedQueueName?: string;
  queueData: QueueData;
  filterOptions: FilterOptions;
  filterOptionsToApply: FilterOptions;
  refetchDuration: number;
  errorMessage: string;
}

export enum QueueMachineEventTypes {
  FETCH_TASKS_QUEUE = "FETCH_TASKS_QUEUE",
  SELECT_QUEUE_NAME = "SELECT_QUEUE_NAME",

  UPDATE_QUEUE_OPTION = "UPDATE_QUEUE_OPTION",
  UPDATE_WORKER_COUNT_OPTION = "UPDATE_WORKER_OPTION",
  UPDATE_LAST_POLL_TIME_OPTION = "UPDATE_LAST_POLL_TIME_OPTION",
}

export type UpdateQueueOptionEvent = {
  type: QueueMachineEventTypes.UPDATE_QUEUE_OPTION;
  queue?: FilterOption;
};

export type UpdateWorkerOptionEvent = {
  type: QueueMachineEventTypes.UPDATE_WORKER_COUNT_OPTION;
  worker?: FilterOption;
};

export type UpdateLastPollTimeOptionEvent = {
  type: QueueMachineEventTypes.UPDATE_LAST_POLL_TIME_OPTION;
  lastPollTime?: FilterOption;
};

export type SelectQueueEvent = {
  type: QueueMachineEventTypes.SELECT_QUEUE_NAME;
  queueName: string;
};

export type FetchQueueEvent = {
  type: QueueMachineEventTypes.FETCH_TASKS_QUEUE;
} & FilterOptions;

export type FetchResponse = { queueData: QueueData; pollData: PollData[] };

export type QueueMonitorMachineEvents =
  | FetchQueueEvent
  | SelectQueueEvent
  | RefreshEvent
  | UpdateQueueOptionEvent
  | UpdateWorkerOptionEvent
  | UpdateLastPollTimeOptionEvent
  | UpdateDurationEvent
  | DoneInvokeEvent<FetchResponse>;
