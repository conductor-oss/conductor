import { ActorRef } from "xstate";
import { WorkflowMetadataEvents } from "pages/definition/WorkflowMetadata/state";
export declare const useWorkflowMetadataEditorActor: (metadataEditorActor: ActorRef<WorkflowMetadataEvents>) => {
    inputParametersActor: any;
    outputParametersActors: any;
    restartableActors: any;
    timeoutSecondsActors: any;
    timeoutPolicyActors: any;
    failureWorkflowActors: any;
    isReady: any;
}[];
