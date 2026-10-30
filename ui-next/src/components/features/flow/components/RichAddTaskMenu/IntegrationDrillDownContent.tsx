import React from "react";
import { Box, Typography, IconButton, Button } from "@mui/material";
import { ArrowRight, Plus, MagnifyingGlass } from "@phosphor-icons/react";
import { IntegrationMenuItem } from "./state/types";
import { IntegrationIcon } from "components/IntegrationIcon";
import { IntegrationDef } from "types";
import { useRichAddTaskMenu } from "./state/hook";
import { ActorRef } from "xstate";
import { RichAddTaskMenuEvents } from "./state/types";

interface IntegrationDrillDownContentProps {
  richAddTaskMenuActor: ActorRef<RichAddTaskMenuEvents>;
  onAddIntegrationTask: (integration: IntegrationMenuItem) => void;
  onAddNewIntegration: (integration: IntegrationDef) => void;
}

export const IntegrationDrillDownContent: React.FC<
  IntegrationDrillDownContentProps
> = ({ richAddTaskMenuActor, onAddIntegrationTask, onAddNewIntegration }) => {
  const [
    {
      availableIntegrations,
      integrationDefs,
      integrationDrillDownMenu,
      searchQuery,
    },
    { handleUpdateIntegrationDrillDown },
  ] = useRichAddTaskMenu(richAddTaskMenuActor);

  if (
    !integrationDrillDownMenu?.isOpen ||
    !integrationDrillDownMenu?.selectedRootIntegration
  ) {
    return null;
  }

  const { selectedRootIntegration } = integrationDrillDownMenu;

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
          Saved connections ({filteredIntegrations?.length})
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
              No saved connections found
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
                onClick={() => onAddIntegrationTask(integration)}
                role="button"
                tabIndex={0}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    onAddIntegrationTask(integration);
                  }
                }}
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
                          integration?.iconName ?? integration?.integrationType
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
                    <Typography sx={{ color: "#6B7280", fontSize: "0.75rem" }}>
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
};
