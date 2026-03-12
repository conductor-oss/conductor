import AutoAwesomeIcon from "@mui/icons-material/AutoAwesome";
import Forum from "@mui/icons-material/Forum";
import UnfoldMore from "@mui/icons-material/UnfoldMore";
import { Box, Button } from "@mui/material";
import { CaretUp, NotePencilIcon } from "@phosphor-icons/react";
import IconButton from "components/MuiIconButton";
import Puller from "components/Puller";
import { AgentContentTab } from "components/agent/agent-types";
import { useAtom } from "jotai";
import React from "react";
import { agentContentTabAtom } from "shared/agent/agentAtomsStore";
import { ActorRef } from "xstate";
import {
  DefinitionMachineEventTypes,
  WorkflowDefinitionEvents,
} from "../state/types";

interface AssistantPanelHeaderProps {
  isAgentExpanded: boolean;
  agentPanelHeight: number | null;
  definitionActor: ActorRef<WorkflowDefinitionEvents>;
  onHeaderMouseDown: (e: React.MouseEvent) => void;
  onHeaderClick: (e: React.MouseEvent) => void;
  onToggleExpanded: () => void;
  onMaximize: () => void;
}

export const AssistantPanelHeader = ({
  isAgentExpanded,
  agentPanelHeight,
  definitionActor,
  onHeaderMouseDown,
  onHeaderClick,
  onToggleExpanded,
  onMaximize,
}: AssistantPanelHeaderProps) => {
  const [agentContentTab, setAgentContentTab] = useAtom(agentContentTabAtom);

  return (
    <Box
      onMouseDown={onHeaderMouseDown}
      onClick={onHeaderClick}
      sx={{
        display: "flex",
        alignItems: "center",
        height: "50px",
        padding: "0 10px",
        cursor: "pointer",
        backgroundColor: "#f5f5f5",
        borderTopLeftRadius: 16,
        borderTopRightRadius: 16,
        "&:hover": {
          backgroundColor: "#eeeeee",
        },
        position: "relative",
        "&::before": {
          content: '""',
          position: "absolute",
          top: 0,
          left: 0,
          right: 0,
          height: "8px",
          cursor: isAgentExpanded ? "row-resize" : "pointer",
          zIndex: 1,
        },
      }}
    >
      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          width: "100%",
        }}
      >
        <Puller sx={{ cursor: "pointer" }} />
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: "12px",
            flex: 1,
          }}
        >
          <AutoAwesomeIcon sx={{ color: "#1976d2", fontSize: 20 }} />
          <Box
            sx={{
              color: "#1a1a1a",
              fontSize: "14px",
              fontWeight: 500,
            }}
          >
            Assistant
          </Box>
        </Box>
        {agentContentTab === AgentContentTab.CONVERSATIONS ? (
          <Button
            variant="text"
            color="secondary"
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              // If collapsed, expand first
              if (!isAgentExpanded) {
                definitionActor.send({
                  type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED,
                });
              }
              setAgentContentTab(AgentContentTab.CHAT);
            }}
            onMouseDown={(e) => {
              e.stopPropagation();
            }}
            startIcon={<NotePencilIcon width={16} height={16} />}
            sx={{
              marginRight: "8px",
              minWidth: "auto",
              "&:hover": {
                backgroundColor: "rgba(0, 0, 0, 0.04)",
              },
            }}
          >
            Chat
          </Button>
        ) : (
          <Button
            variant="text"
            color="secondary"
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              // If collapsed, expand first
              if (!isAgentExpanded) {
                definitionActor.send({
                  type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED,
                });
              }
              setAgentContentTab(AgentContentTab.CONVERSATIONS);
            }}
            onMouseDown={(e) => {
              e.stopPropagation();
            }}
            startIcon={<Forum sx={{ width: "16px" }} />}
            sx={{
              marginRight: "8px",
              minWidth: "auto",
              "&:hover": {
                backgroundColor: "rgba(0, 0, 0, 0.04)",
              },
            }}
          >
            Conversations
          </Button>
        )}
        {isAgentExpanded && agentPanelHeight !== null && (
          <IconButton
            size="small"
            onClick={(e) => {
              e.stopPropagation();
              onMaximize();
            }}
            onMouseDown={(e) => {
              e.stopPropagation();
            }}
            sx={{
              marginRight: "8px",
              flexShrink: 0,
              minWidth: "32px",
              width: "32px",
              height: "32px",
            }}
            title="Expand to full height"
          >
            <UnfoldMore sx={{ width: "16px" }} />
          </IconButton>
        )}
        <IconButton
          size="small"
          onClick={(e) => {
            e.stopPropagation();
            onToggleExpanded();
          }}
          sx={{
            marginRight: "8px",
          }}
        >
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: "30px",
              height: "30px",
              borderRadius: "8px",
              transition: "transform 0.2s",
              transform: isAgentExpanded ? "rotate(180deg)" : "rotate(0deg)",
            }}
          >
            <CaretUp size={20} color="#1a1a1a" />
          </Box>
        </IconButton>
      </Box>
    </Box>
  );
};
