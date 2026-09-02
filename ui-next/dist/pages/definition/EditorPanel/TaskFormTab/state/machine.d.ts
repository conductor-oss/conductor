import { TaskFormEvents, TaskFormMachineContext } from "./types";
export declare const formMachine: import("xstate").StateMachine<TaskFormMachineContext, any, TaskFormEvents, {
    value: any;
    context: TaskFormMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, TaskFormEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
