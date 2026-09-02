import { ActorRef } from "xstate";
import { WorkflowMetadataEvents } from "pages/definition/WorkflowMetadata/state";
import { SchemaFormValue } from "../../TaskFormTab/forms/SchemaForm";
export declare const useWorkflowMetadataEditorActor: (metadataEditorActor: ActorRef<WorkflowMetadataEvents>) => {
    inputParametersActor: any;
    outputParametersActors: any;
    restartableActors: any;
    timeoutSecondsActors: any;
    timeoutPolicyActors: any;
    failureWorkflowActors: any;
    isReady: any;
    nameFieldActor: any;
    descriptionFieldActor: any;
    inputSchemaFieldActor: any;
    outputSchemaFieldActor: any;
    enforceSchemaFieldActor: any;
    workflowStatusListenerEnabledActor: any;
    workflowStatusListenerSinkActor: any;
    rateLimitConfigActor: any;
}[];
export declare const useWorkflowMetadata: (metadataEditorActor: ActorRef<WorkflowMetadataEvents>) => readonly [{
    readonly wUpdateTime: any;
    readonly ownerEmail: any;
    readonly currentWorkflowName: any;
    readonly workflowStatusListenerEnabled: any;
    readonly fastAppCreation: any;
    readonly installScriptMetadata: any;
    readonly readmeMetadata: any;
    readonly inputSchema: any;
    readonly outputSchema: any;
    readonly enforceSchema: any;
}, {
    readonly removeMetadataAttribs: () => void;
    readonly updateSchemaForm: (inputSchema?: SchemaFormValue, outputSchema?: SchemaFormValue, enforceSchema?: boolean) => void;
}];
