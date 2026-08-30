import React, { useCallback, useContext, useMemo, useState } from "react";
import { Box, Chip, Tooltip } from "@mui/material";
import {
  CopySimple as CopyIcon,
  Trash as DeleteIcon,
} from "@phosphor-icons/react";
import { Button, DataTable, IconButton, NavLink, Paper } from "components";
import { FilterObjectItem } from "components/ui/DataTable/state";
import { ColumnCustomType, LegacyColumn } from "components/ui/DataTable/types";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import SectionContainer from "components/ui/layout/SectionContainer";
import PlayIcon from "components/icons/PlayIcon";
import { useAuth } from "components/features/auth";
import { MessageContext } from "components/providers/messageContext";
import { Helmet } from "react-helmet";
import { useNavigate } from "react-router";
import AzureIcon from "images/svg/azure-icon.svg";
import BedrockIcon from "images/svg/bedrock-icon.svg";
import OrkesIcon from "images/svg/orkes-icon.svg";
import { PopoverMessage } from "types/Messages";
import {
  AGENT_DEFINITION_URL,
  AGENT_EXECUTIONS_URL,
  RUN_AGENT_URL,
} from "utils/constants/route";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { logger } from "utils/logger";
import { useActionWithPath, useFetch } from "utils/query";
import { tryToJson } from "utils/utils";
import CloneAgentDialog from "./CloneAgentDialog";
import { AgentSummary } from "./types";

const EXTERNAL_TYPES = new Set(["microsoft-foundry", "bedrock"]);

function providerLabel(type?: string | null): string {
  switch (type) {
    case "microsoft-foundry": return "Microsoft Foundry";
    case "bedrock": return "Bedrock";
    default: return "Conductor";
  }
}

function providerColor(type?: string | null): string {
  switch (type) {
    case "microsoft-foundry": return "#0078d4";
    case "bedrock": return "#e07730";
    default: return "#1565c0";
  }
}

function providerIcon(type?: string | null): string {
  switch (type) {
    case "microsoft-foundry": return AzureIcon;
    case "bedrock": return BedrockIcon;
    default: return OrkesIcon;
  }
}

const INTRO_CONTENT = `**Agents** are AI agent definitions compiled and run as native Conductor workflows by the embedded Conductor Agents runtime.

No agents deployed yet? Use **Create Agent** for a copy-and-run SDK guide.`;


export default function AgentDefinitions() {
  const navigate = useNavigate();
  const { isTrialExpired } = useAuth();
  const { data, isFetching, refetch } = useFetch<AgentSummary[]>("/agent/list");
  const { setMessage } = useContext(MessageContext);
  const [toastMessage, setToastMessage] = useState<PopoverMessage | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<AgentSummary | null>(null);
  const [agentToClone, setAgentToClone] = useState<AgentSummary | null>(null);
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [
    { filterParam, pageParam, searchParam },
    { setFilterParam, setSearchParam, handlePageChange },
  ] = useCustomPagination();
  const filterObj =
    filterParam === "" ? undefined : tryToJson<FilterObjectItem>(filterParam);

  const deleteAgentAction = useActionWithPath({
    onSuccess: () => {
      setToastMessage({
        text: "Agent deleted successfully",
        severity: "success",
      });
      refetch();
    },
    onError: (error: Error) => {
      setMessage({ severity: "error", text: "Failed to delete agent" });
      logger.error(error);
    },
  });

  const columns = useMemo<LegacyColumn[]>(
    () => [
      {
        id: "workflow_name",
        name: "name",
        label: "Workflow name",
        renderer: (name: string, agent: AgentSummary) => (
          <Box sx={{ display: "flex", alignItems: "center", gap: 0.75 }}>
            <img
              src={providerIcon(agent.type)}
              alt={providerLabel(agent.type)}
              width={18}
              height={18}
              style={{ flexShrink: 0 }}
            />
            <NavLink
              data-cy="workflow-link"
              path={`${AGENT_DEFINITION_URL.BASE}/${encodeURIComponent(name.trim())}/${agent.version}`}
              id={`${name.trim()}-link-btn`}
            >
              {name.trim()}
            </NavLink>
          </Box>
        ),
        tooltip: "The name of the workflow",
      },
      {
        id: "workflow_description",
        name: "description",
        label: "Description",
        grow: 2,
        tooltip: "The description of the workflow",
      },
      {
        id: "create_time",
        name: "createTime",
        label: "Created time",
        type: ColumnCustomType.DATE,
        tooltip: "The time the workflow was created",
      },
      {
        id: "latest_version",
        name: "version",
        label: "Latest version",
        grow: 0.5,
        tooltip: "The latest version of the workflow",
      },
      {
        id: "schema_version",
        name: "schemaVersion",
        label: "Schema version",
        grow: 0.5,
        tooltip: "The schema version of the workflow",
      },
      {
        id: "restartable",
        name: "restartable",
        label: "Restartable",
        grow: 0.5,
        tooltip: "Whether the workflow is restartable",
      },
      {
        id: "status_listener_enabled",
        name: "workflowStatusListenerEnabled",
        label: "Status listener enabled",
        grow: 0.5,
        tooltip: "Whether the status listener is enabled",
      },
      {
        id: "owner_email",
        name: "ownerEmail",
        label: "Owner email",
        tooltip: "The email of the owner of the workflow",
      },
      {
        id: "input_params",
        name: "inputParameters",
        label: "Input params",
        type: ColumnCustomType.JSON,
        sortable: false,
        tooltip: "The input parameters of the workflow",
      },
      {
        id: "output_params",
        name: "outputParameters",
        label: "Output params",
        type: ColumnCustomType.JSON,
        sortable: false,
        tooltip: "The output parameters of the workflow",
      },
      {
        id: "timeout_policy",
        name: "timeoutPolicy",
        label: "Timeout policy",
        grow: 0.5,
        tooltip: "The timeout policy of the workflow",
      },
      {
        id: "timeout_seconds",
        name: "timeoutSeconds",
        label: "Timeout seconds",
        grow: 0.5,
        tooltip: "The timeout seconds of the workflow",
      },
      {
        id: "failure_workflow",
        name: "failureWorkflow",
        label: "Failure workflow",
        grow: 1,
        tooltip: "The compensation workflow",
      },
      {
        id: "executions_link",
        name: "name",
        label: "Executions",
        sortable: false,
        searchable: false,
        grow: 0.5,
        renderer: (name: string) => (
          <NavLink
            path={`${AGENT_EXECUTIONS_URL.BASE}?agentName=${encodeURIComponent(name.trim())}`}
            newTab
          >
            Query
          </NavLink>
        ),
        tooltip: "The executions of the workflow",
      },
      {
        id: "actions",
        name: "name",
        label: "Actions",
        sortable: false,
        searchable: false,
        grow: 0.5,
        minWidth: "180px",
        tooltip: "Actions you can perform on the workflow",
        renderer: (_: string, agent: AgentSummary) => {
          const isExternal = agent.type && EXTERNAL_TYPES.has(agent.type);
          return (
            <Box style={{ display: "flex", justifyContent: "space-evenly" }}>
              <Tooltip title="Run agent">
                <IconButton
                  id={`run-${agent.name}-btn`}
                  disabled={isTrialExpired}
                  onClick={() =>
                    navigate(RUN_AGENT_URL, {
                      state: {
                        agentName: agent.name,
                        agentVersion: agent.version,
                      },
                    })
                  }
                  size="small"
                >
                  <PlayIcon size={22} />
                </IconButton>
              </Tooltip>
              {!isExternal && (
                <Tooltip title="Clone Agent">
                  <IconButton
                    id={`clone-${agent.name}-btn`}
                    disabled={isTrialExpired}
                    onClick={() => setAgentToClone(agent)}
                    size="small"
                  >
                    <CopyIcon size={20} />
                  </IconButton>
                </Tooltip>
              )}
              <Tooltip title="Delete workflow">
                <IconButton
                  id={`delete-${agent.name}-btn`}
                  disabled={isTrialExpired}
                  onClick={() => setConfirmDelete(agent)}
                  size="small"
                >
                  <DeleteIcon size={20} />
                </IconButton>
              </Tooltip>
            </Box>
          );
        },
      },
    ],
    [isTrialExpired, navigate],
  );

  const handleFilterChange = useCallback(
    (filter?: FilterObjectItem) =>
      setFilterParam(filter ? JSON.stringify(filter) : ""),
    [setFilterParam],
  );

  const allAgents = useMemo<AgentSummary[]>(
    () => (Array.isArray(data) ? data : []),
    [data],
  );

  // Unique provider types present in the data
  const providerTypes = useMemo<string[]>(() => {
    const types = new Set<string>();
    allAgents.forEach((a) => types.add(a.type ?? "conductor"));
    return [...types].sort();
  }, [allAgents]);

  // Agents filtered to selected provider chip
  const tableData = useMemo<AgentSummary[]>(() => {
    if (selectedType === null) return allAgents;
    return allAgents.filter((a) =>
      selectedType === "conductor"
        ? !a.type || !EXTERNAL_TYPES.has(a.type)
        : a.type === selectedType,
    );
  }, [allAgents, selectedType]);

  return (
    <>
      <Helmet>
        <title>Agent Definitions</title>
      </Helmet>
      {agentToClone && (
        <CloneAgentDialog
          selectedAgent={agentToClone}
          agentList={tableData}
          onClose={() => setAgentToClone(null)}
          onSuccess={() => {
            setAgentToClone(null);
            refetch();
            setToastMessage({
              text: "Agent cloned successfully",
              severity: "success",
            });
          }}
        />
      )}
      {confirmDelete && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(confirmed) => {
            if (confirmed) {
              deleteAgentAction.mutate({
                method: "delete",
                path: `/agent/${encodeURIComponent(confirmDelete.name)}?version=${confirmDelete.version}`,
              });
            }
            setConfirmDelete(null);
          }}
          message={
            <>
              Are you sure you want to delete{" "}
              <strong style={{ color: "red" }}>{confirmDelete.name}</strong>{" "}
              workflow definition? This cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type <strong>{confirmDelete.name}</strong> to confirm.
              </div>
            </>
          }
          header="Deletion confirmation"
          isInputConfirmation
          valueToBeDeleted={confirmDelete.name}
        />
      )}
      <SectionHeader
        _deprecate_marginTop={0}
        title="Agent Definitions"
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Run agent",
                color: "secondary",
                onClick: () => navigate(RUN_AGENT_URL),
                startIcon: <PlayIcon />,
              },
              {
                label: "Create Agent",
                color: "secondary",
                onClick: () =>
                  navigate(
                    `${AGENT_DEFINITION_URL.NEW}?language=python&framework=native`,
                  ),
              },
            ]}
          />
        }
      />
      <SectionContainer>
        <Paper id="workflow-definitions-table-wrapper" variant="outlined">
          <Header loading={isFetching} />
          <DataTable
            localStorageKey="agentDefinitionsTable"
            quickSearchEnabled
            quickSearchPlaceholder="Search agent definitions"
            searchTerm={searchParam ?? ""}
            onSearchTermChange={setSearchParam}
            defaultShowColumns={[
              "workflow_name",
              "workflow_description",
              "latest_version",
              "create_time",
              "owner_email",
              "executions_link",
              "actions",
            ]}
            keyField="name"
            onFilterChange={handleFilterChange}
            initialFilterObj={filterObj}
            data={tableData}
            columns={columns}
            customActions={[
              <Tooltip
                title="Refresh agent definitions"
                key="refresh-agent-definitions"
              >
                <Button
                  variant="text"
                  color="inherit"
                  size="small"
                  onClick={refetch as () => void}
                >
                  Refresh
                </Button>
              </Tooltip>,
            ]}
            customStyles={{ subHeader: { style: { backgroundColor: "transparent", paddingLeft: 8, paddingRight: 8 } } }}
            subHeader={providerTypes.length > 1}
            subHeaderComponent={
              <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", py: 0.5, width: "100%" }}>
                <Chip
                  label={
                    <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                      <img src={OrkesIcon} alt="" width={14} height={14} />
                      All ({allAgents.length})
                    </Box>
                  }
                  onClick={() => setSelectedType(null)}
                  variant={selectedType === null ? "filled" : "outlined"}
                  sx={{ borderColor: selectedType === null ? undefined : "#888", fontWeight: selectedType === null ? 600 : 400 }}
                  clickable
                />
                {providerTypes.map((type) => {
                  const count = type === "conductor"
                    ? allAgents.filter((a) => !a.type || !EXTERNAL_TYPES.has(a.type)).length
                    : allAgents.filter((a) => a.type === type).length;
                  const active = selectedType === type;
                  return (
                    <Chip
                      key={type}
                      label={
                        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                          <img src={providerIcon(type)} alt="" width={14} height={14} />
                          {providerLabel(type)} ({count})
                        </Box>
                      }
                      onClick={() => setSelectedType(active ? null : type)}
                      variant={active ? "filled" : "outlined"}
                      sx={{
                        borderColor: active ? providerColor(type) : "#888",
                        backgroundColor: active ? providerColor(type) : undefined,
                        color: active ? "#fff" : providerColor(type),
                        fontWeight: active ? 600 : 400,
                      }}
                      clickable
                    />
                  );
                })}
              </Box>
            }
            onChangePage={handlePageChange}
            paginationDefaultPage={pageParam ? Number(pageParam) : 1}
            noDataComponent={
              <NoDataComponent
                title="Agent Definition"
                description={INTRO_CONTENT}
                buttonText="Create Agent"
                buttonHandler={() =>
                  navigate(
                    `${AGENT_DEFINITION_URL.NEW}?language=python&framework=native`,
                  )
                }
              />
            }
          />
        </Paper>
      </SectionContainer>
      {toastMessage && (
        <SnackbarMessage
          autoHideDuration={3000}
          id="agent-definitions-toast-message"
          message={toastMessage.text}
          severity={toastMessage.severity}
          onDismiss={() => setToastMessage(null)}
        />
      )}
    </>
  );
}
