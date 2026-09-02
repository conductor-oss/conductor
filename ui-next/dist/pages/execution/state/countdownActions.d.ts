import { COUNT_DOWN_TYPE, CountdownContext, UpdateDurationEvent } from "./types";
export declare const updateCountdownDuration: import("xstate").AssignAction<CountdownContext, UpdateDurationEvent, UpdateDurationEvent>;
export declare const resetCountdownElapsed: import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateCountdownType: (type: COUNT_DOWN_TYPE) => import("xstate").AssignAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const updateParentDuration: import("xstate").SendAction<CountdownContext, any, import("xstate").AnyEventObject>;
export declare const updateParentIsDisabled: (isDisabled?: boolean) => import("xstate").SendAction<CountdownContext, import("xstate").EventObject, import("xstate").AnyEventObject>;
