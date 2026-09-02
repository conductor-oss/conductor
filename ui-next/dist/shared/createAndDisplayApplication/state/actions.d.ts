import { DoneInvokeEvent } from "xstate";
import { CreateAndDisplayApplicationMachineContext } from "./types";
export declare const persistApplicationKeys: import("xstate").AssignAction<CreateAndDisplayApplicationMachineContext, DoneInvokeEvent<{
    id: string;
    secret: string;
}>, DoneInvokeEvent<{
    id: string;
    secret: string;
}>>;
export declare const persistApplicationId: import("xstate").AssignAction<CreateAndDisplayApplicationMachineContext, DoneInvokeEvent<{
    id: string;
}>, DoneInvokeEvent<{
    id: string;
}>>;
export declare const persistError: import("xstate").AssignAction<CreateAndDisplayApplicationMachineContext, DoneInvokeEvent<{
    message: string;
}>, DoneInvokeEvent<{
    message: string;
}>>;
export declare const clearError: import("xstate").AssignAction<CreateAndDisplayApplicationMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
