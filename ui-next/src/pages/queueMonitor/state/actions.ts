import { assign, DoneInvokeEvent, forwardTo } from "xstate";
import {
  QueueMonitorMachineContext,
  FetchQueueEvent,
  PollData,
  FetchResponse,
  SelectQueueEvent,
  UpdateQueueOptionEvent,
  UpdateWorkerOptionEvent,
  UpdateLastPollTimeOptionEvent,
} from "./types";
import { UpdateDurationEvent } from "../refresher";
import _groupBy from "lodash/groupBy";
import _isNil from "lodash/isNil";
import _path from "lodash/fp/path";

export const persistFetchRequestParams = assign<
  QueueMonitorMachineContext,
  FetchQueueEvent
>((_context, { type: _type, ...rest }) => {
  return {
    filterOptions: rest,
    filterOptionsToApply: rest,
  };
});

export const persistPollQueueData = assign<
  QueueMonitorMachineContext,
  DoneInvokeEvent<FetchResponse>
>((_context, { data }) => ({
  pollDataByQueueName: _groupBy(
    data.pollData,
    "queueName",
  ) as unknown as Record<string, PollData[]>,
  queueData: data.queueData,
}));

export const persistQueueSelection = assign<
  QueueMonitorMachineContext,
  SelectQueueEvent
>((context, { queueName }) => ({
  selectedQueueName: queueName,
  noWorkers: _isNil(_path(queueName, context.pollDataByQueueName)),
}));

export const persistQueueOption = assign<
  QueueMonitorMachineContext,
  UpdateQueueOptionEvent
>({
  filterOptionsToApply: ({ filterOptionsToApply }, { queue }) => ({
    ...filterOptionsToApply,
    queue,
  }),
});

export const persistWorkerOption = assign<
  QueueMonitorMachineContext,
  UpdateWorkerOptionEvent
>({
  filterOptionsToApply: ({ filterOptionsToApply }, { worker }) => ({
    ...filterOptionsToApply,
    worker,
  }),
});

export const persistLastPollTimeOption = assign<
  QueueMonitorMachineContext,
  UpdateLastPollTimeOptionEvent
>({
  filterOptionsToApply: ({ filterOptionsToApply }, { lastPollTime }) => ({
    ...filterOptionsToApply,
    lastPollTime,
  }),
});

export const peristErrorMessage = assign<
  QueueMonitorMachineContext,
  DoneInvokeEvent<{ message: string }>
>({ errorMessage: (_context: any, { data }: any) => data.message });

export const persistDuration = assign<
  QueueMonitorMachineContext,
  UpdateDurationEvent
>({
  refetchDuration: (context, { value }) => value,
});

export const persistLocalStorageDuration = assign<
  QueueMonitorMachineContext,
  DoneInvokeEvent<number>
>({
  refetchDuration: (context, { data }) => {
    return data;
  },
});

export const forwardToRefreshMachine = forwardTo("refreshMachine");
