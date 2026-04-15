import { Box } from "@mui/material";
import Agent from "components/features/agent/Agent";
import { AgentDisplayMode } from "components/features/agent/agent-types";
import React, { useEffect, useRef } from "react";
import { ActorRef } from "xstate";
import { WorkflowDefinitionEvents } from "../state/types";
import { AssistantPanelHeader } from "./AssistantPanelHeader";

export const SHRINKED_HEIGHT = 430;

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
  onHeightChange: (height: number) => void;
  isResizing: boolean;
}

export const AssistantPanel = ({
  isAgentExpanded,
  agentPanelHeight,
  tabsHeight,
  errorInspectorActor,
  definitionActor,
  onHeaderMouseDown,
  onHeaderClick,
  onToggleExpanded,
  onMaximize,
  onHeightChange,
  isResizing,
}: AssistantPanelProps) => {
  const agentPanelRef = useRef<HTMLDivElement>(null);

  // Handle clicks outside the assistant panel to resize it to SHRINKED_HEIGHT (currently 430px)
  useEffect(() => {
    if (!isAgentExpanded) return;

    const handleClickOutside = (event: MouseEvent) => {
      if (
        agentPanelRef.current &&
        !agentPanelRef.current.contains(event.target as Node) &&
        !isResizing
      ) {
        onHeightChange(SHRINKED_HEIGHT);
      }
    };

    document.addEventListener("mousedown", handleClickOutside);
    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [isAgentExpanded, isResizing, onHeightChange]);

  return (
    <Box
      ref={agentPanelRef}
      sx={{
        position: "absolute",
        display: "flex",
        flexDirection: "column",
        bottom: errorInspectorActor ? "50px" : 0,
        left: 0,
        width: "100%",
        height: isAgentExpanded
          ? agentPanelHeight !== null
            ? `${agentPanelHeight}px`
            : errorInspectorActor
              ? `calc(100% - ${tabsHeight}px - 50px)`
              : `calc(100% - ${tabsHeight}px)`
          : "50px", // Fixed height when collapsed for smooth animation
        top:
          isAgentExpanded && agentPanelHeight === null
            ? `${tabsHeight}px`
            : "auto",
        background: "#ffffff",
        borderTopLeftRadius: 16,
        borderTopRightRadius: 16,
        overflow: "hidden",
        borderTop: isAgentExpanded ? "1px solid rgba(0, 0, 0, 0.12)" : "none",
        zIndex: 11,
        boxShadow: "0 -2px 8px rgba(0, 0, 0, 0.1)",
        // Animate height and top for smooth expansion/collapse
        transition: isResizing
          ? "none"
          : "height 0.3s cubic-bezier(0.4, 0, 0.2, 1), top 0.3s cubic-bezier(0.4, 0, 0.2, 1), border-top 0.3s ease",
      }}
    >
      <AssistantPanelHeader
        isAgentExpanded={isAgentExpanded}
        agentPanelHeight={agentPanelHeight}
        definitionActor={definitionActor}
        onHeaderMouseDown={onHeaderMouseDown}
        onHeaderClick={onHeaderClick}
        onToggleExpanded={onToggleExpanded}
        onMaximize={onMaximize}
      />
      {isAgentExpanded && (
        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            overflow: "hidden",
            display: "flex",
            flexDirection: "column",
            opacity: 1,
            // Disable all transitions during resize to prevent content animation
            transition: isResizing
              ? "none"
              : "opacity 0.3s cubic-bezier(0.4, 0, 0.2, 1)",
            // Prevent content from animating during resize
            transform: isResizing ? "none" : undefined,
          }}
        >
          <Agent
            mode={AgentDisplayMode.TABBED}
            hideHeader={true}
            sx={{ paddingBottom: 0 }}
          />
        </Box>
      )}
    </Box>
  );
};
