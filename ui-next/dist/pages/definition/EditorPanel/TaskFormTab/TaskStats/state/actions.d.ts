import { DoneInvokeEvent } from "xstate";
import { TaskStatsMachineContext, TaskStatsResponse, ChangeTaskStatStartTimeEvent, UpdateTaskNameEvent } from "./types";
export declare const persistMetrics: import("xstate").AssignAction<TaskStatsMachineContext, DoneInvokeEvent<TaskStatsResponse>, DoneInvokeEvent<TaskStatsResponse>>;
export declare const persistStartTimeStamp: import("xstate").AssignAction<TaskStatsMachineContext, ChangeTaskStatStartTimeEvent, ChangeTaskStatStartTimeEvent>;
export declare const persistTaskName: import("xstate").AssignAction<TaskStatsMachineContext, UpdateTaskNameEvent, UpdateTaskNameEvent>;
