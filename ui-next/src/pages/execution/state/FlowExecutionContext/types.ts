import { ReactNode } from "react";

export interface FlowExecutionContextProviderProps {
  onExpandDynamic: (name: string) => void;
  onCollapseDynamic: (name: string) => void;
  /** Opens the right panel on a task the diagram does not draw as a node. */
  onSelectTask?: (selection: { ref?: string; taskId?: string }) => void;
  children?: ReactNode;
}
