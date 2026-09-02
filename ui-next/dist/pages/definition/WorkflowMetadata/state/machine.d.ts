import { WorkflowMetadataMachineContext, WorkflowMetadataEvents } from "./types";
export declare const workflowMetadataMachine: import("xstate").StateMachine<WorkflowMetadataMachineContext, any, WorkflowMetadataEvents, {
    value: any;
    context: WorkflowMetadataMachineContext;
}, import("xstate").BaseActionObject, import("xstate").ServiceMap, import("xstate").ResolveTypegenMeta<import("xstate").TypegenDisabled, WorkflowMetadataEvents, import("xstate").BaseActionObject, import("xstate").ServiceMap>>;
