import { FunctionComponent, ReactNode } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../types";
import { WorkflowEditContext } from "./WorkflowEditContext";

interface WorkflowEditContextProps {
  workflowDefinitionActor?: ActorRef<WorkflowDefinitionEvents>;
  children?: ReactNode;
}

export const FlowEditContextProvider: FunctionComponent<
  WorkflowEditContextProps
> = ({ workflowDefinitionActor, children }) => (
  <WorkflowEditContext.Provider
    value={{
      workflowDefinitionActor,
    }}
  >
    {children}
  </WorkflowEditContext.Provider>
);
