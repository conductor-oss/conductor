import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../types";

export interface WorkflowEditContextProps {
  workflowDefinitionActor?: ActorRef<WorkflowDefinitionEvents>;
  children?: ReactNode;
}
