import { RefreshMachineContext, TimerEvents } from "./types";
export declare const timerMachine: import("xstate").StateMachine<RefreshMachineContext, any, TimerEvents, {
    value: any;
    context: RefreshMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, TimerEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
