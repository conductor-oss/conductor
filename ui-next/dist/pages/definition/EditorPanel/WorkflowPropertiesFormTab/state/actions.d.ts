import { MetadataFieldMachineContext, ChangeValueEvent } from "./types";
export declare const persistChanges: import("xstate").AssignAction<MetadataFieldMachineContext, ChangeValueEvent, ChangeValueEvent>;
export declare const addSomeKey: import("xstate").AssignAction<MetadataFieldMachineContext, any, any>;
export declare const debounceSyncWithParent: import("xstate").SendAction<MetadataFieldMachineContext, ChangeValueEvent, import("xstate").AnyEventObject>;
export declare const cancelSyncWithParent: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
