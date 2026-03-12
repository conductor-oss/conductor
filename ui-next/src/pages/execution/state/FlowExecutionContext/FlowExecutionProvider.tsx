import { FunctionComponent } from "react";
import { FlowExecutionContext } from "./FlowExecutionContext";
import { FlowExecutionContextProviderProps } from "./types";

export const FlowExecutionContextProvider: FunctionComponent<
  FlowExecutionContextProviderProps
> = ({ children, onExpandDynamic, onCollapseDynamic }) => (
  <FlowExecutionContext.Provider value={{ onExpandDynamic, onCollapseDynamic }}>
    {children}
  </FlowExecutionContext.Provider>
);
