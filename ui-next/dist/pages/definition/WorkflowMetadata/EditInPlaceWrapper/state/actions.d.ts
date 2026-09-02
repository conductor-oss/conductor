import { EditInPlaceMachineContext, ChangeValueEvent } from "./types";
export declare const persistChanges: import("xstate").AssignAction<EditInPlaceMachineContext, ChangeValueEvent, ChangeValueEvent>;
export declare const debounceSyncWithParent: import("xstate").SendAction<EditInPlaceMachineContext, ChangeValueEvent, import("xstate").AnyEventObject>;
export declare const cancelSyncWithParent: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
