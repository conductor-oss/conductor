import { RefreshMachineContext, UpdateDurationEvent } from "./types";
export declare const persistDuration: import("xstate").AssignAction<RefreshMachineContext, UpdateDurationEvent, UpdateDurationEvent>;
export declare const persistElapsed: import("xstate").AssignAction<RefreshMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const sendRefresh: import("xstate").SendAction<unknown, import("xstate").EventObject, import("xstate").AnyEventObject>;
export declare const forwardToParent: import("xstate").SendAction<unknown, import("xstate").EventObject, import("xstate").AnyEventObject>;
export declare const restartTimer: import("xstate").AssignAction<RefreshMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
