import { FunctionComponent } from "react";
import { FlowExecutionContext } from "./FlowExecutionContext";
import { FlowExecutionContextProviderProps } from "./types";

export const FlowExecutionContextProvider: FunctionComponent<
  FlowExecutionContextProviderProps
> = ({ children, onExpandDynamic, onCollapseDynamic, onSelectTask }) => (
  <FlowExecutionContext.Provider
    value={{ onExpandDynamic, onCollapseDynamic, onSelectTask }}
  >
    {children}
  </FlowExecutionContext.Provider>
);
