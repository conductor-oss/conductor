import { createContext } from "react";
import { WorkflowMetadataContextProviderProps } from "./types";

export const WorkflowMetadataContext =
  createContext<WorkflowMetadataContextProviderProps>({
    workflowMetadataActor: undefined,
  });
