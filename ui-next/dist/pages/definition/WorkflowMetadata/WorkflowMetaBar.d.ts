import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state";
interface WorkflowMetaBarProps {
    leftPanelExpanded: boolean;
    setLeftPanelExpanded: (t: boolean) => void;
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
}
export declare const WorkflowMetaBar: FunctionComponent<WorkflowMetaBarProps>;
export {};
