import { ReactNode } from "react";

export interface FlowExecutionContextProviderProps {
  onExpandDynamic: (name: string) => void;
  onCollapseDynamic: (name: string) => void;
  /** Opens the right panel on a task the diagram does not draw as a node. */
  onSelectTask?: (selection: { ref?: string; taskId?: string }) => void;
  /**
   * Set by the execution page: the diagram shows a run, not a definition being edited. An agent
   * the editor has no card snapshot for is then not "unresolved": the server found it to run it.
   */
  inExecution?: boolean;
  children?: ReactNode;
}
