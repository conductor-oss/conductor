import { TaskStatsMachineContext, TaskStatsEvents } from "./types";
export declare const taskStatsMachine: import("xstate").StateMachine<TaskStatsMachineContext, any, TaskStatsEvents, {
    value: any;
    context: TaskStatsMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, TaskStatsEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
