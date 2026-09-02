import { DoneInvokeEvent } from "xstate";
import { QueueMonitorMachineContext, FetchQueueEvent, FetchResponse, SelectQueueEvent, UpdateQueueOptionEvent, UpdateWorkerOptionEvent, UpdateLastPollTimeOptionEvent } from "./types";
import { UpdateDurationEvent } from "../refresher";
export declare const persistFetchRequestParams: import("xstate").AssignAction<QueueMonitorMachineContext, FetchQueueEvent, FetchQueueEvent>;
export declare const persistPollQueueData: import("xstate").AssignAction<QueueMonitorMachineContext, DoneInvokeEvent<FetchResponse>, DoneInvokeEvent<FetchResponse>>;
export declare const persistQueueSelection: import("xstate").AssignAction<QueueMonitorMachineContext, SelectQueueEvent, SelectQueueEvent>;
export declare const persistQueueOption: import("xstate").AssignAction<QueueMonitorMachineContext, UpdateQueueOptionEvent, UpdateQueueOptionEvent>;
export declare const persistWorkerOption: import("xstate").AssignAction<QueueMonitorMachineContext, UpdateWorkerOptionEvent, UpdateWorkerOptionEvent>;
export declare const persistLastPollTimeOption: import("xstate").AssignAction<QueueMonitorMachineContext, UpdateLastPollTimeOptionEvent, UpdateLastPollTimeOptionEvent>;
export declare const peristErrorMessage: import("xstate").AssignAction<QueueMonitorMachineContext, DoneInvokeEvent<{
    message: string;
}>, DoneInvokeEvent<{
    message: string;
}>>;
export declare const persistDuration: import("xstate").AssignAction<QueueMonitorMachineContext, UpdateDurationEvent, UpdateDurationEvent>;
export declare const persistLocalStorageDuration: import("xstate").AssignAction<QueueMonitorMachineContext, DoneInvokeEvent<number>, DoneInvokeEvent<number>>;
export declare const forwardToRefreshMachine: import("xstate").SendAction<unknown, import("xstate").EventObject, any>;
