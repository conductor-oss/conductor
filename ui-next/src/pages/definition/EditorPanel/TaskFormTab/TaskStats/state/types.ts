import { AuthHeaders } from "types/common";

export type PrometheusRateData = Array<[number, string]>;

export interface RateTimeCount {
  time: string;
  count: number;
}

export interface TaskStatsMachineContext {
  completedRateSeries: PrometheusRateData;
  failedRateSeries: PrometheusRateData;
  completedAmount: number;
  failedAmount: number;
  scheduledAmount: number;
  startHoursBack: number;
  authHeaders?: AuthHeaders;
  taskName: string;
}

type Metric = {
  taskType: string;
  status: "COMPLETED" | "FAILED";
};

type PrometheusResponse = {
  data: {
    result: Array<{ metric: Metric; values: PrometheusRateData }>;
  };
};

type PrometheusCurrentResponse = {
  data: {
    result: Array<{ metric: Metric; value: [number, string] }>;
  };
};

export interface TaskStatsResponse {
  completed: PrometheusResponse;
  failed: PrometheusResponse;
  current: PrometheusCurrentResponse;
}

export enum TaskStatsEventTypes {
  CHANGE_START_TIME = "CHANGE_START_TIME",
  UPDATE_TASK_NAME = "UPDATE_TASK_NAME",
}

export type ChangeTaskStatStartTimeEvent = {
  type: TaskStatsEventTypes.CHANGE_START_TIME;
  value: number;
};

export type UpdateTaskNameEvent = {
  type: TaskStatsEventTypes.UPDATE_TASK_NAME;
  name: string;
};

export type TaskStatsEvents =
  | ChangeTaskStatStartTimeEvent
  | UpdateTaskNameEvent;
