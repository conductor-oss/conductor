import { CountdownContext, CountdownEvents } from "./types";
export declare const countdownMachine: import("xstate").StateMachine<CountdownContext, any, CountdownEvents, {
    value: any;
    context: CountdownContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, CountdownEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
