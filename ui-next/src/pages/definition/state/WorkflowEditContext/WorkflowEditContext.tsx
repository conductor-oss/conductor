import { createContext } from "react";
import { WorkflowEditContextProps } from "./types";

export const WorkflowEditContext = createContext<WorkflowEditContextProps>({
  workflowDefinitionActor: undefined,
});
