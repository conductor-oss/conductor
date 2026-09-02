import { FilterOptions, RangeOptions, QueueMonitorMachineEvents } from "../state";
import { ActorRef } from "xstate";
export declare enum FormReducerActionTypes {
    UPDATE_QUEUE_OPTION = "UPDATE_QUEUE_OPTION",
    UPDATE_WORKER_COUNT_OPTION = "UPDATE_WORKER_OPTION",
    UPDATE_LAST_POLL_TIME_OPTION = "UPDATE_LAST_POLL_TIME_OPTION"
}
type Payload = {
    option: RangeOptions;
    size: number;
} | undefined;
export interface ReducerAction {
    type: FormReducerActionTypes;
    payload: Payload;
}
export declare const useFilterUpdate: (queueMachineActor: ActorRef<QueueMonitorMachineEvents>) => [FilterOptions, any, boolean, string];
export {};
