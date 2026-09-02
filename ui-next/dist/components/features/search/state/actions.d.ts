import { DoneInvokeEvent } from "xstate";
import { PersistSearchTermEvent, SearchMachineContext } from "./types";
import { WorkflowDef } from "types/WorkflowDef";
export declare const persistSearchTerm: import("xstate").AssignAction<SearchMachineContext, PersistSearchTermEvent, PersistSearchTermEvent>;
export declare const persistTaskNames: import("xstate").AssignAction<SearchMachineContext, DoneInvokeEvent<{
    name: string;
    description?: string;
}[]>, DoneInvokeEvent<{
    name: string;
    description?: string;
}[]>>;
export declare const persistWorkflowNames: import("xstate").AssignAction<SearchMachineContext, DoneInvokeEvent<WorkflowDef[]>, DoneInvokeEvent<WorkflowDef[]>>;
export declare const persistScheduleNames: import("xstate").AssignAction<SearchMachineContext, DoneInvokeEvent<string[]>, DoneInvokeEvent<string[]>>;
export declare const persistEventNames: import("xstate").AssignAction<SearchMachineContext, DoneInvokeEvent<string[]>, DoneInvokeEvent<string[]>>;
export declare const persistErrorMessage: import("xstate").AssignAction<SearchMachineContext, DoneInvokeEvent<{
    message: string;
}>, DoneInvokeEvent<{
    message: string;
}>>;
