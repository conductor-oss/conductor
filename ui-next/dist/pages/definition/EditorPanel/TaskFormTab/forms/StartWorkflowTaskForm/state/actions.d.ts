import { DoneInvokeEvent } from "xstate";
import { SelectWorkflowNameEvent, StartSubWfNameVersionMachineContext } from "./types";
export declare const persistWfName: import("xstate").AssignAction<StartSubWfNameVersionMachineContext, SelectWorkflowNameEvent, SelectWorkflowNameEvent>;
export declare const persistFetchedNamesAndVersions: import("xstate").AssignAction<StartSubWfNameVersionMachineContext, DoneInvokeEvent<Map<string, number[]>>, DoneInvokeEvent<Map<string, number[]>>>;
export declare const persistOptions: import("xstate").AssignAction<StartSubWfNameVersionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
