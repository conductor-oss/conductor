import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
export interface RunWorkflowButtonProps {
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
    disabled: boolean;
}
export declare const RunWorkflowButton: FunctionComponent<RunWorkflowButtonProps>;
