import { TaskDefinitionMachineContext, TaskDefinitionMachineEvent } from "./types";
export declare const taskDefinitionMachine: import("xstate").StateMachine<TaskDefinitionMachineContext, any, TaskDefinitionMachineEvent, {
    value: any;
    context: TaskDefinitionMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, TaskDefinitionMachineEvent, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
