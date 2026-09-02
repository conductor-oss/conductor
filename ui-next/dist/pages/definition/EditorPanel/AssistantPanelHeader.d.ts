import React from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
interface AssistantPanelHeaderProps {
    isAgentExpanded: boolean;
    agentPanelHeight: number | null;
    definitionActor: ActorRef<WorkflowDefinitionEvents>;
    onHeaderMouseDown: (e: React.MouseEvent) => void;
    onHeaderClick: (e: React.MouseEvent) => void;
    onToggleExpanded: () => void;
    onMaximize: () => void;
}
export declare const AssistantPanelHeader: ({ isAgentExpanded, agentPanelHeight, definitionActor, onHeaderMouseDown, onHeaderClick, onToggleExpanded, onMaximize, }: AssistantPanelHeaderProps) => React.JSX.Element;
export {};
