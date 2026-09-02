import { FunctionComponent } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "./state/types";
export interface HeaderActionButtonsProps {
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
}
export declare const PromptIfChanges: FunctionComponent<HeaderActionButtonsProps>;
