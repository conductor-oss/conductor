import { FunctionComponent } from "react";
import { WorkflowMetadataContext } from "./WorkflowMetadataContext";
import { WorkflowMetadataContextProviderProps } from "./types";

export const WorkflowMetadataProvider: FunctionComponent<
  WorkflowMetadataContextProviderProps
> = ({ children, workflowMetadataActor }) => (
  <WorkflowMetadataContext.Provider value={{ workflowMetadataActor }}>
    {children}
  </WorkflowMetadataContext.Provider>
);
