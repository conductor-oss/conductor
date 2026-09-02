import { ExecutionMachineEvents, ExecutionMachineContext } from "./types";
export declare const executionMachine: import("xstate").StateMachine<ExecutionMachineContext, any, ExecutionMachineEvents, {
    value: any;
    context: ExecutionMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, ExecutionMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
