import { ActorRef } from "xstate";
import { WorkflowMetadataEvents } from "./types";
export declare const useWorkflowMetadataEditorActor: (metadataEditorActor: ActorRef<WorkflowMetadataEvents>) => {
    ownerEmail: any;
    updateTime: any;
    isDisabled: any;
    nameFieldActor: any;
    descriptionFieldActor: any;
}[];
