import { assign, DoneInvokeEvent } from "xstate";
import {
  TaskStatsMachineContext,
  TaskStatsResponse,
  ChangeTaskStatStartTimeEvent,
  UpdateTaskNameEvent,
} from "./types";
import _first from "lodash/first";
import _last from "lodash/last";

type CurrentRes = { [key in "completed" | "failed"]: number };

export const persistMetrics = assign<
  TaskStatsMachineContext,
  DoneInvokeEvent<TaskStatsResponse>
>((__context, { data: { completed, failed, current } }) => {
  const currentMetric = current.data.result.reduce(
    (acc, c): CurrentRes => ({
      ...acc,
      [c.metric.status.toLowerCase() as "completed" | "failed"]: parseInt(
        _last(c.value) as string,
        10,
      ),
    }),
    {
      completed: 0,
      failed: 0,
    },
  );
  return {
    completedRateSeries: _first(completed.data.result)?.values || [],
    failedRateSeries: _first(failed.data.result)?.values || [],
    completedAmount: currentMetric?.completed,
    failedAmount: currentMetric?.failed,
    scheduledAmount: Object.values(currentMetric).reduce(
      (acc, c) => acc + c,
      0,
    ),
  };
});

export const persistStartTimeStamp = assign<
  TaskStatsMachineContext,
  ChangeTaskStatStartTimeEvent
>({
  startHoursBack: (__context, { value }) => value,
});

export const persistTaskName = assign<
  TaskStatsMachineContext,
  UpdateTaskNameEvent
>({
  taskName: (__, { name }) => name,
});
