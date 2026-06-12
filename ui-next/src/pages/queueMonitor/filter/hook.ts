import {
  FilterOption,
  FilterOptions,
  RangeOptions,
  QueueMonitorMachineEvents,
  QueueMachineEventTypes,
} from "../state";
import { filterOptionToQueryParams, hasNoQueryParams } from "../helpers";
import { useLocation } from "react-router";
import { ActorRef } from "xstate";
import { useSelector, useActor } from "@xstate/react";
import fastDeepEquals from "fast-deep-equal";

export enum FormReducerActionTypes {
  UPDATE_QUEUE_OPTION = "UPDATE_QUEUE_OPTION",
  UPDATE_WORKER_COUNT_OPTION = "UPDATE_WORKER_OPTION",
  UPDATE_LAST_POLL_TIME_OPTION = "UPDATE_LAST_POLL_TIME_OPTION",
}

type Payload =
  | {
      option: RangeOptions;
      size: number;
    }
  | undefined;

export interface ReducerAction {
  type: FormReducerActionTypes;
  payload: Payload;
}

export const useFilterUpdate = (
  queueMachineActor: ActorRef<QueueMonitorMachineEvents>,
): [FilterOptions, any, boolean, string] => {
  const location = useLocation();
  const [, send] = useActor(queueMachineActor);
  const filterOptions = useSelector(
    queueMachineActor,
    (state) => state.context.filterOptionsToApply,
  );

  const originalFilterOptions = useSelector(
    queueMachineActor,
    (state) => state.context.filterOptions,
  );

  const queryParams = filterOptionToQueryParams(filterOptions);

  const handleUpdateQueue = (queue: FilterOption | undefined) =>
    send({
      type: QueueMachineEventTypes.UPDATE_QUEUE_OPTION,
      queue,
    });

  const handleUpdateWorkerCount = (worker: FilterOption | undefined) =>
    send({
      type: QueueMachineEventTypes.UPDATE_WORKER_COUNT_OPTION,
      worker,
    });

  const handleUpdateLastPollFilter = (lastPollTime: FilterOption | undefined) =>
    send({
      type: QueueMachineEventTypes.UPDATE_LAST_POLL_TIME_OPTION,
      lastPollTime,
    });

  const isDisabled = fastDeepEquals(originalFilterOptions, filterOptions);

  const clearAllFields = () => {
    handleUpdateQueue(undefined);
    handleUpdateWorkerCount(undefined);
    handleUpdateLastPollFilter(undefined);
  };

  return [
    filterOptions,
    {
      handleUpdateQueue,
      handleUpdateWorkerCount,
      handleUpdateLastPollFilter,
      clearAllFields,
    },
    isDisabled,
    hasNoQueryParams(filterOptions)
      ? location.pathname
      : `${location.pathname}?${queryParams}`,
  ];
};
