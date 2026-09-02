import { WorkflowMetadataEvents } from "pages/definition/WorkflowMetadata/state";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
export interface WorkflowPropertiesFormProps {
    workflowMetadataActor: ActorRef<WorkflowMetadataEvents>;
}
export declare const WorkflowPropertiesForm: FunctionComponent<WorkflowPropertiesFormProps>;
