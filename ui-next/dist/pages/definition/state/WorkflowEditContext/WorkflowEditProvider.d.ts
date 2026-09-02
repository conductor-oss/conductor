import { FunctionComponent, ReactNode } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../types";
interface WorkflowEditContextProps {
    workflowDefinitionActor?: ActorRef<WorkflowDefinitionEvents>;
    children?: ReactNode;
}
export declare const FlowEditContextProvider: FunctionComponent<WorkflowEditContextProps>;
export {};
