import { RunMachineContext, RunMachineEvents } from "./types";
export declare const runMachine: import("xstate").StateMachine<RunMachineContext, any, RunMachineEvents, {
    value: any;
    context: RunMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, RunMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
