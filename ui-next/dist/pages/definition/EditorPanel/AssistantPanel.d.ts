import React from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
interface AssistantPanelProps {
    isAgentExpanded: boolean;
    agentPanelHeight: number | null;
    tabsHeight: number;
    errorInspectorActor: any;
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
    onHeaderMouseDown: (e: React.MouseEvent) => void;
    onHeaderClick: (e: React.MouseEvent) => void;
    onToggleExpanded: () => void;
    onMaximize: () => void;
    isResizing: boolean;
}
export declare const AssistantPanel: ({ isAgentExpanded, agentPanelHeight, tabsHeight, errorInspectorActor, definitionActor, onHeaderMouseDown, onHeaderClick, onToggleExpanded, onMaximize, isResizing, }: AssistantPanelProps) => React.JSX.Element;
export {};
