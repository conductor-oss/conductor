import { ReactNode } from "react";
import { ActorRef } from "xstate";
import { WorkflowMetadataEvents } from "../types";

export interface WorkflowMetadataContextProviderProps {
  workflowMetadataActor?: ActorRef<WorkflowMetadataEvents>;
  children?: ReactNode;
}
