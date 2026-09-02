import { TaskDefinitionFormContext } from "pages/definition/task/form/state";
import { TaskDefinitionDto } from "types/TaskDefinition";
import { DoneInvokeEvent } from "xstate";
import { DebounceHandleChangeTaskDefinitionEvent, HandleChangeTaskDefinitionEvent, SetInputParametersEvent, SetSaveConfirmationOpenEvent, SetTaskDefinitionEvent, SetTaskDomainEvent, TaskDefinitionMachineContext } from "./types";
export declare const handleChangeTaskDefinition: import("xstate").AssignAction<TaskDefinitionMachineContext, HandleChangeTaskDefinitionEvent, HandleChangeTaskDefinitionEvent>;
export declare const debounceChangeTaskDefinition: import("xstate").SendAction<TaskDefinitionMachineContext, DebounceHandleChangeTaskDefinitionEvent, import("xstate").AnyEventObject>;
export declare const cancelDebounceChangeTaskDefinition: import("xstate").CancelAction<unknown, import("xstate").EventObject, import("xstate").EventObject>;
export declare const persistTaskDefinitionByName: import("xstate").AssignAction<TaskDefinitionMachineContext, DoneInvokeEvent<TaskDefinitionDto>, DoneInvokeEvent<TaskDefinitionDto>>;
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
export declare const changeIsContinueCreate: import("xstate").AssignAction<TaskDefinitionMachineContext, SetSaveConfirmationOpenEvent, SetSaveConfirmationOpenEvent>;
export declare const updateOriginTaskDefinition: import("xstate").AssignAction<TaskDefinitionMachineContext, DoneInvokeEvent<TaskDefinitionDto>, DoneInvokeEvent<TaskDefinitionDto>>;
export declare const setIsEditTaskDef: import("xstate").AssignAction<TaskDefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const resetContext: import("xstate").AssignAction<TaskDefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const prepareNewTaskContext: import("xstate").AssignAction<TaskDefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const setInputParameters: import("xstate").AssignAction<TaskDefinitionMachineContext, SetInputParametersEvent, SetInputParametersEvent>;
export declare const setTaskDomain: import("xstate").AssignAction<TaskDefinitionMachineContext, SetTaskDomainEvent, SetTaskDomainEvent>;
export declare const persistWorkflowId: import("xstate").AssignAction<TaskDefinitionMachineContext, DoneInvokeEvent<string>, DoneInvokeEvent<string>>;
export declare const syncDataFromFormMachine: import("xstate").AssignAction<TaskDefinitionMachineContext, DoneInvokeEvent<TaskDefinitionFormContext & {
    reason: string;
}>, DoneInvokeEvent<TaskDefinitionFormContext & {
    reason: string;
}>>;
export declare const cleanLastSelectedTab: import("xstate").AssignAction<TaskDefinitionMachineContext, import("xstate").EventObject, import("xstate").EventObject>;
export declare const setNameOnOriginTaskDefinition: import("xstate").AssignAction<TaskDefinitionFormContext, SetTaskDefinitionEvent, SetTaskDefinitionEvent>;
