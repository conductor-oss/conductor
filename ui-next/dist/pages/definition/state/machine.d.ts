import { DefinitionMachineContext, WorkflowDefinitionEvents } from "./types";
export declare const workflowDefinitionMachine: import("xstate").StateMachine<DefinitionMachineContext, any, WorkflowDefinitionEvents, {
    value: any;
    context: DefinitionMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, WorkflowDefinitionEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
