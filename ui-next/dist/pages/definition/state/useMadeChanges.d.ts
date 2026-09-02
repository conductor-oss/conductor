import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./types";
export declare const useWorkflowChanges: (service: ActorRef<WorkflowDefinitionEvents>) => {
    isNewWorkflow: any;
    currentWf: any;
    workflowChanges: any;
    madeChanges: boolean;
};
