import { WorkflowDef } from "types/WorkflowDef";
import { DoneInvokeEvent } from "xstate";
import { LocalCopyMachineContext } from "./types";
import { WorkflowWithNoErrorsEvent } from "../../errorInspector/state";
export declare const storeLocalCopy: import("xstate").AssignAction<LocalCopyMachineContext, DoneInvokeEvent<Partial<WorkflowDef>>, DoneInvokeEvent<Partial<WorkflowDef>>>;
export declare const sendLocalChanges: import("xstate").SendAction<LocalCopyMachineContext, any, import("xstate").AnyEventObject>;
export declare const persistLastStoredVersion: import("xstate").AssignAction<LocalCopyMachineContext, WorkflowWithNoErrorsEvent, WorkflowWithNoErrorsEvent>;
export declare const cleanLocalChanges: import("xstate").AssignAction<LocalCopyMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
