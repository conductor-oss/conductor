import React from "react";
import {
  Box,
  Typography,
  CircularProgress,
  IconButton,
  Button,
} from "@mui/material";
import { ArrowRight, Plus, MagnifyingGlass } from "@phosphor-icons/react";
import { IntegrationMenuItem } from "./state/types";
import { IntegrationIcon } from "components/IntegrationIcon";
import { getInitials } from "utils/utils";
import { IntegrationDef } from "types";
import { useRichAddTaskMenu } from "./state/hook";
import { ActorRef } from "xstate";
import { RichAddTaskMenuEvents } from "./state/types";

interface IntegrationDrillDownContentProps {
  richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>;
  onAddToolTask: (tool: any) => void;
  onAddNewIntegration: (integration: IntegrationDef) => void;
}

export const IntegrationDrillDownContent: React.FC<
  IntegrationDrillDownContentProps
> = ({ richAddTaskMenuActor, onAddToolTask, onAddNewIntegration }) => {
  const [
    {
      availableIntegrations,
      integrationDefs,
      integrationDrillDownMenu,
      isFetchingIntegrationTools,
      searchQuery,
    },
    {
      handleFetchIntegrationTools,
      handleUpdateIntegrationDrillDown,
      handleTyping,
    },
  ] = useRichAddTaskMenu(richAddTaskMenuActor);

  if (
    !integrationDrillDownMenu?.isOpen ||
    !integrationDrillDownMenu?.selectedRootIntegration
  ) {
    return null;
  }

  const {
    level,
    selectedIntegration,
    selectedRootIntegration,
    selectedIntegrationTools,
  } = integrationDrillDownMenu;

  const handleNavigateToTools = (integration: IntegrationMenuItem) => {
    handleTyping("");
    handleFetchIntegrationTools(integration);
  };

  const handleNavigateBack = () => {
    handleTyping("");
    handleUpdateIntegrationDrillDown({
      ...integrationDrillDownMenu,
      selectedIntegration: null,
      selectedRootIntegration:
        integrationDrillDownMenu?.level === "tools"
          ? integrationDrillDownMenu?.selectedRootIntegration
          : null,
      selectedIntegrationTools: null,
      level: "integrations",
    });
  };

  const handleCloseDrillDown = () => {
    handleUpdateIntegrationDrillDown({
      isOpen: false,
      selectedIntegration: null,
      selectedRootIntegration: null,
      level: "integrations",
    });
  };

  const filterBySearchQuery = (items: any[], fields: string[]) => {
    if (!searchQuery) return items;
    const query = searchQuery?.toLowerCase();
    return items?.filter((item) =>
      fields?.some((field) => item[field]?.toLowerCase()?.includes(query)),
    );
  };
  const filteredIntegrations = filterBySearchQuery(
    (availableIntegrations || []).filter(
      (integration: IntegrationMenuItem) =>
        integration?.integrationType ===
        selectedRootIntegration?.integrationType,
    ),
    ["name"],
  );
  const filteredTools = filterBySearchQuery(selectedIntegrationTools || [], [
    "api",
  ]);

  if (level === "integrations") {
    return (
      <Box sx={{ height: "100%" }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
          <IconButton
            onClick={handleCloseDrillDown}
            sx={{ minWidth: "auto", p: 0.5 }}
          >
            <ArrowRight size={16} style={{ transform: "rotate(180deg)" }} />
          </IconButton>
          <Typography sx={{ fontSize: "0.875rem", fontWeight: 600 }}>
            {selectedRootIntegration?.name}
          </Typography>
          <Box sx={{ ml: "auto" }}>
            <Button
              variant="text"
              size="small"
              sx={{
                fontWeight: "normal",
                fontSize: "12px",
                color: "#6B7280",
                "&:hover": {
                  color: "#111827",
                },
              }}
              startIcon={<Plus size={16} />}
              onClick={() => {
                const template = integrationDefs.find(
                  (integration: IntegrationDef) =>
                    integration?.name === selectedRootIntegration?.name,
                );
                if (template) {
                  onAddNewIntegration(template);
                }
              }}
            >
              Add New
            </Button>
          </Box>
        </Box>
        <Box pb={2}>
          <Typography
            sx={{ fontSize: "12px", fontWeight: 500, color: "#6B7280" }}
          >
            Integrations ({filteredIntegrations?.length})
          </Typography>
        </Box>

        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            gap: 1,
            flex: 1,
            overflow: "auto",
            height: "100%",
          }}
        >
          {filteredIntegrations?.length === 0 ? (
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
                height: "80%",
                gap: 1.5,
                color: "#64748B",
              }}
            >
              <MagnifyingGlass size={24} />
              <Typography sx={{ fontSize: "0.855rem" }}>
                No integrations found
              </Typography>
            </Box>
          ) : (
            filteredIntegrations?.map(
              (integration: IntegrationMenuItem, idx: number) => (
                <Box
                  key={`${integration?.name}_${integration?.category}_${integration?.description}_${idx}`}
                  sx={{
                    background: "#FFFFFF",
                    border: "1px solid #F0F0F0",
                    borderRadius: 2,
                    p: 1.5,
                    cursor: "pointer",
                    transition: "all 0.2s ease",
                    boxShadow: "0 1px 2px rgba(0, 0, 0, 0.05)",
                    "&:hover": {
                      backgroundColor: "#F9FAFB",
                      borderColor: "#E5E7EB",
                      transform: "translateY(-1px)",
                      boxShadow:
                        "0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)",
                      "& .task-icon-box": {
                        backgroundColor: "rgba(59, 130, 246, 0.08)",
                      },
                    },
                  }}
                  onClick={() => handleNavigateToTools(integration)}
                >
                  <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                    <Box
                      className="task-icon-box"
                      sx={{
                        width: 40,
                        height: 40,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        background: "#F9FAFB",
                        borderRadius: 1.5,
                        mr: 2,
                        color: "#6B7280",
                        transition: "all 0.2s ease",
                        flexShrink: 0,
                      }}
                    >
                      <Box
                        sx={{
                          width: 20,
                          height: 20,
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                        }}
                      >
                        <IntegrationIcon
                          integrationName={
                            integration?.iconName ??
                            integration?.integrationType
                          }
                        />
                      </Box>
                    </Box>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography
                        sx={{
                          fontWeight: 600,
                          fontSize: "0.8125rem",
                          color: "#111827",
                        }}
                      >
                        {integration?.name}
                      </Typography>
                      <Typography
                        sx={{ color: "#6B7280", fontSize: "0.75rem" }}
                      >
                        {integration?.description}
                      </Typography>
                    </Box>
                    <ArrowRight size={14} color="#6B7280" />
                  </Box>
                </Box>
              ),
            )
          )}
        </Box>
      </Box>
    );
  }

  if (level === "tools") {
    return (
      <Box sx={{ height: "100%" }}>
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 2,
            mb: 2,
            pt: 1,
            pb: 1,
          }}
        >
          <IconButton
            onClick={handleNavigateBack}
            sx={{ minWidth: "auto", p: 0.5 }}
          >
            <ArrowRight size={16} style={{ transform: "rotate(180deg)" }} />
          </IconButton>
          <Typography sx={{ fontSize: "0.875rem", fontWeight: 600 }}>
            {selectedRootIntegration?.name} - {selectedIntegration?.name}
          </Typography>
        </Box>
        <Box pb={2}>
          <Typography
            sx={{ fontSize: "12px", fontWeight: 500, color: "#6B7280" }}
          >
            Tools ({filteredTools?.length})
          </Typography>
        </Box>

        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            gap: 1,
            flex: 1,
            overflow: "auto",
            height: "100%",
          }}
        >
          {isFetchingIntegrationTools ? (
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                py: 4,
              }}
            >
              <CircularProgress size={24} sx={{ color: "#2563EB" }} />
            </Box>
          ) : filteredTools?.length === 0 ? (
            <Box
              sx={{
                display: "flex",
                flexDirection: "column",
                justifyContent: "center",
                alignItems: "center",
                height: "80%",
                gap: 1.5,
                color: "#64748B",
              }}
            >
              <MagnifyingGlass size={24} />
              <Typography sx={{ fontSize: "0.855rem" }}>
                No tools found
              </Typography>
            </Box>
          ) : (
            filteredTools?.map((tool: any, idx: number) => (
              <Box
                key={tool?.api + idx}
                sx={{
                  display: "flex",
                  alignItems: "flex-start",
                  gap: 2,
                  p: 2,
                  borderRadius: 1,
                  cursor: "pointer",
                  background: "#FFFFFF",
                  border: "1px solid #F0F0F0",
                  transition: "all 0.2s ease",
                  "&:hover": {
                    backgroundColor: "#F9FAFB",
                    borderColor: "#E5E7EB",
                    transform: "translateY(-1px)",
                    boxShadow: "0 2px 4px rgba(0, 0, 0, 0.05)",
                  },
                }}
                onClick={() => onAddToolTask(tool)}
              >
                <Box
                  sx={{
                    width: 40,
                    height: 40,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    background: "#F3F4F6",
                    borderRadius: 1,
                    flexShrink: 0,
                  }}
                >
                  {tool?.integrationType ? (
                    <div
                      style={{
                        width: "20px",
                        height: "20px",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <IntegrationIcon
                        integrationName={tool?.integrationType}
                      />
                    </div>
                  ) : (
                    <Typography
                      sx={{
                        fontSize: "0.80rem",
                        fontWeight: 500,
                        color: "#64748B",
                      }}
                    >
                      {getInitials(tool?.api)}
                    </Typography>
                  )}
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <Typography
                    sx={{
                      fontWeight: 500,
                      fontSize: "0.80rem",
                      color: "#111827",
                      mb: 0.25,
                      overflowWrap: "break-word",
                    }}
                  >
                    {tool?.api}
                  </Typography>
                  <Typography
                    sx={{
                      color: "#6B7280",
                      fontSize: "0.75rem",
                      overflowWrap: "break-word",
                      whiteSpace: "nowrap",
                      overflow: "hidden",
                      textOverflow: "ellipsis",
                    }}
                  >
                    {tool?.description}
                  </Typography>
                </Box>
              </Box>
            ))
          )}
        </Box>
      </Box>
    );
  }

  return null;
};
