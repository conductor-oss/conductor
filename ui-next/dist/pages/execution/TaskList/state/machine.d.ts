import { TaskListMachineContext } from "./types";
export declare const taskListMachine: () => import("xstate").StateMachine<TaskListMachineContext, any, import("xstate").AnyEventObject, {
    value: any;
    context: TaskListMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, import("xstate").AnyEventObject, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
