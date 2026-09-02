import { TaskFormHeaderMachineContext, ChangeNameValueEvent, ValuesUpdatedEvent, StartEditingValuesEvent, StopEditingValuesEvent } from "./types";
export declare const persistNameChanges: import("xstate").AssignAction<TaskFormHeaderMachineContext, ChangeNameValueEvent, ChangeNameValueEvent>;
export declare const persistTaskReferenceNameChanges: import("xstate").AssignAction<TaskFormHeaderMachineContext, ChangeNameValueEvent, ChangeNameValueEvent>;
export declare const persistChanges: import("xstate").AssignAction<TaskFormHeaderMachineContext, ValuesUpdatedEvent, ValuesUpdatedEvent>;
export declare const syncWithParent: import("xstate").SendAction<TaskFormHeaderMachineContext, import("xstate").EventObject, import("xstate").AnyEventObject>;
export declare const generateTaskReferenceAndName: import("xstate").AssignAction<TaskFormHeaderMachineContext, any, any>;
export declare const cancelSyncWithParent: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const startEditingValues: import("xstate").AssignAction<TaskFormHeaderMachineContext, StartEditingValuesEvent, StartEditingValuesEvent>;
export declare const stopEditingValues: import("xstate").AssignAction<TaskFormHeaderMachineContext, StopEditingValuesEvent, StopEditingValuesEvent>;
