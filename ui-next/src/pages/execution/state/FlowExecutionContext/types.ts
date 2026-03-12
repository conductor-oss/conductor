import { ReactNode } from "react";

export interface FlowExecutionContextProviderProps {
  onExpandDynamic: (name: string) => void;
  onCollapseDynamic: (name: string) => void;
  children?: ReactNode;
}
