import { DoneInvokeEvent } from "xstate";
import { UpdateMetaDataEvent, WorkflowChangedEvent, WorkflowMetadataMachineContext } from "./types";
export declare const persistPartialMetaDataChanges: import("xstate").AssignAction<WorkflowMetadataMachineContext, UpdateMetaDataEvent, UpdateMetaDataEvent>;
export declare const syncWithParent: import("xstate").SendAction<unknown, UpdateMetaDataEvent, import("xstate").AnyEventObject>;
export declare const cancelSyncWithParent: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateLocalCopy: import("xstate").AssignAction<WorkflowMetadataMachineContext, WorkflowChangedEvent, WorkflowChangedEvent>;
export declare const spawnFieldActors: import("xstate").AssignAction<WorkflowMetadataMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const notifyActors: import("xstate").PureAction<unknown, import("xstate").EventObject, import("xstate").AnyEventObject>;
export declare const forwardActionToActors: import("xstate").PureAction<unknown, import("xstate").EventObject, any>;
export declare const persistApplicationKeys: import("xstate").AssignAction<WorkflowMetadataMachineContext, DoneInvokeEvent<{
    id: string;
    secret: string;
}>, DoneInvokeEvent<{
    id: string;
    secret: string;
}>>;
