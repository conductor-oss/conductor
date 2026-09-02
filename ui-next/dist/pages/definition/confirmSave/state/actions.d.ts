import { WorkflowDef } from "types/WorkflowDef";
import { DoneInvokeEvent } from "xstate";
import { EditEvent, SaveWorkflowMachineContext } from "./types";
export declare const editChanges: import("xstate").AssignAction<SaveWorkflowMachineContext, EditEvent, EditEvent>;
export declare const storeResolvedAgentSnapshots: import("xstate").AssignAction<SaveWorkflowMachineContext, DoneInvokeEvent<string>, DoneInvokeEvent<string>>;
export declare const debounceEditEvent: import("xstate").RaiseAction<SaveWorkflowMachineContext, EditEvent, EditEvent>;
export declare const cancelDebounceEditChanges: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateWorkflowVersionAndName: import("xstate").AssignAction<SaveWorkflowMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const reportServerErrors: import("xstate").SendAction<SaveWorkflowMachineContext, DoneInvokeEvent<{
    text: string;
    validationErrors: {
        message?: string;
        path?: string;
    }[];
}>, any>;
export declare const cleanServerErrors: import("xstate").SendAction<SaveWorkflowMachineContext, DoneInvokeEvent<{
    text: string;
}>, any>;
export declare const sendSuccessSave: import("xstate").SendAction<SaveWorkflowMachineContext, any, import("xstate").AnyEventObject>;
export declare const sendCancelSave: import("xstate").SendAction<SaveWorkflowMachineContext, any, import("xstate").AnyEventObject>;
export declare const checkForErrorsInWorkflow: import("xstate").SendAction<SaveWorkflowMachineContext, any, any>;
export declare const grabLastVersionAndPersistAsNew: import("xstate").AssignAction<SaveWorkflowMachineContext, DoneInvokeEvent<WorkflowDef[]>, DoneInvokeEvent<WorkflowDef[]>>;
