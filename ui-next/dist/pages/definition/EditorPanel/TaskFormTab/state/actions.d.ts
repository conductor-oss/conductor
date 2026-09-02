import { UpdateTaskEvent, TaskFormMachineContext, UpdateCrumbsEvent, ForceRefreshTaskEvent, SelectEdgeEvent } from "./types";
export declare const spawnTaskHeaderMachineActor: import("xstate").AssignAction<TaskFormMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateTask: import("xstate").AssignAction<TaskFormMachineContext, UpdateTaskEvent, UpdateTaskEvent>;
export declare const updateCollapseWorkflowList: import("xstate").SendAction<TaskFormMachineContext, any, import("xstate").AnyEventObject>;
export declare const updateCrumbsAndOriginalTask: import("xstate").AssignAction<TaskFormMachineContext, UpdateCrumbsEvent, UpdateCrumbsEvent>;
/** Used by AI agent updates — always replaces task state regardless of task type. */
export declare const forceRefreshTask: import("xstate").AssignAction<TaskFormMachineContext, ForceRefreshTaskEvent, ForceRefreshTaskEvent>;
export declare const maybePersistSelectedSwitchBranch: import("xstate").AssignAction<TaskFormMachineContext, SelectEdgeEvent, SelectEdgeEvent>;
export declare const notifyChanges: import("xstate").SendAction<TaskFormMachineContext, import("xstate").EventObject, import("xstate").AnyEventObject>;
export declare const notifyNameChange: import("xstate").SendAction<TaskFormMachineContext, any, import("xstate").AnyEventObject>;
export declare const updateTaskHeaderMachine: import("xstate").PureAction<TaskFormMachineContext, any, any>;
