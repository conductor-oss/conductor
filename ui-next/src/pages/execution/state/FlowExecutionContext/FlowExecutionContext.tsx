import { createContext } from "react";
import { FlowExecutionContextProviderProps } from "./types";

export const FlowExecutionContext =
  createContext<FlowExecutionContextProviderProps>({
    onExpandDynamic: () => {},
    onCollapseDynamic: () => {},
  });
