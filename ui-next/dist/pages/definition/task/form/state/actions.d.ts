import { DoneInvokeEvent } from "xstate";
import { HandleChangeTaskFormEvent, TaskDefinitionFormContext } from "pages/definition/task/form/state/types";
export declare const handleChangeTask: import("xstate").AssignAction<TaskDefinitionFormContext, HandleChangeTaskFormEvent, HandleChangeTaskFormEvent>;
export declare const persistError: import("xstate").AssignAction<TaskDefinitionFormContext, DoneInvokeEvent<{
    error: {
        [key: string]: any;
    };
    numberOfError: number;
}>, DoneInvokeEvent<{
    error: {
        [key: string]: any;
    };
    numberOfError: number;
}>>;
export declare const persistErrorMessage: import("xstate").AssignAction<TaskDefinitionFormContext, DoneInvokeEvent<{
    message: string;
}>, DoneInvokeEvent<{
    message: string;
}>>;
export declare const resetForm: import("xstate").AssignAction<TaskDefinitionFormContext, import("xstate").EventObject, import("xstate").EventObject>;
