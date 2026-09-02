import { CodeMachineContext, CodeMachineEvents } from "./types";
export declare const codeMachine: import("xstate").StateMachine<CodeMachineContext, any, CodeMachineEvents, {
    value: any;
    context: CodeMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, CodeMachineEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
