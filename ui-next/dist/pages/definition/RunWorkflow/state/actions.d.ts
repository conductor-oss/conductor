import { DoneInvokeEvent } from "xstate";
import { ClearFormEvent, HandlePopoverMessageEvent, RunMachineContext, UpdateAllFieldsEvent, UpdateCorrelationIdEvent, UpdateIdempotencyKeyEvent, UpdateInputParamsEvent, UpdateTasksToDomainEvent, UpdateIdempotencyStrategyEvent, UpdateIdempotencyValuesEvent } from "./types";
export declare const persistInputParams: import("xstate").AssignAction<RunMachineContext, UpdateInputParamsEvent, UpdateInputParamsEvent>;
export declare const persistCorrelationId: import("xstate").AssignAction<RunMachineContext, UpdateCorrelationIdEvent, UpdateCorrelationIdEvent>;
export declare const persistIdempotencyKey: import("xstate").AssignAction<RunMachineContext, UpdateIdempotencyKeyEvent, UpdateIdempotencyKeyEvent>;
export declare const persistIdempotencyStrategy: import("xstate").AssignAction<RunMachineContext, UpdateIdempotencyStrategyEvent, UpdateIdempotencyStrategyEvent>;
export declare const persistIdempotencyValues: import("xstate").AssignAction<RunMachineContext, UpdateIdempotencyValuesEvent, UpdateIdempotencyValuesEvent>;
export declare const persistTasksToDomain: import("xstate").AssignAction<RunMachineContext, UpdateTasksToDomainEvent, UpdateTasksToDomainEvent>;
export declare const clearForm: import("xstate").AssignAction<RunMachineContext, ClearFormEvent, ClearFormEvent>;
export declare const checkForExistingInputParams: import("xstate").AssignAction<RunMachineContext, DoneInvokeEvent<any>, DoneInvokeEvent<any>>;
export declare const persistPopupMessage: import("xstate").AssignAction<RunMachineContext, HandlePopoverMessageEvent, HandlePopoverMessageEvent>;
export declare const redirectToNewExecution: import("xstate").SendAction<RunMachineContext, {
    type: string;
    data: string;
}, import("xstate").AnyEventObject>;
export declare const persistAllFields: import("xstate").AssignAction<RunMachineContext, UpdateAllFieldsEvent, UpdateAllFieldsEvent>;
export declare const reportErrorToErrorInspector: import("xstate").SendAction<unknown, DoneInvokeEvent<{
    message: string;
}>, any>;
export declare const sendContextToParent: import("xstate").SendAction<RunMachineContext, import("xstate").EventObject, import("xstate").AnyEventObject>;
