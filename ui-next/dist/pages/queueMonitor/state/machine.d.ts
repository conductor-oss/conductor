import { QueueMonitorMachineContext, QueueMonitorMachineEvents } from "./types";
export declare const queueMonitorMachine: import("xstate").StateMachine<QueueMonitorMachineContext, any, QueueMonitorMachineEvents, {
    value: any;
    context: QueueMonitorMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, QueueMonitorMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
