import { Box, Tooltip } from "@mui/material";
import {
  CopySimple as CopyIcon,
  Trash as DeleteIcon,
  Tag as TagIcon,
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
import { useCallback, useContext, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useNavigate } from "react-router";
import { PopoverMessage } from "types/Messages";
import { TagDto } from "types/Tag";
import {
  AGENT_DEFINITION_URL,
  AGENT_EXECUTIONS_URL,
  RUN_AGENT_URL,
} from "utils/constants/route";
import { featureFlags, FEATURES } from "utils/flags";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { logger } from "utils/logger";
import { useActionWithPath, useFetch } from "utils/query";
import { tryToJson } from "utils/utils";
import TagList from "components/ui/TagList";
import TagChip from "components/ui/TagChip";
import AddTagDialog from "components/features/tags/AddTagDialog";
import CloneAgentDialog from "./CloneAgentDialog";
import { AgentSummary } from "./types";

const INTRO_CONTENT = `**Agents** are AI agent definitions compiled and run as native Conductor workflows by the embedded Conductor Agents runtime.

No agents deployed yet? Use **Create Agent** for a copy-and-run SDK guide.`;

// AgentSummary.tags carries compiler-derived capabilities (`tool-calling`,
// `multi-agent-*`, `simple`) — not user tags. They describe how an agent is composed,
// are recomputed on every deploy, and are not grantable. Rendered as bare chips in
// their own column: they have no key, so TagList's `key:value` label would read
// "capability:tool-calling" under a column already titled Capabilities.
const CapabilityList = ({
  capabilities,
  name,
}: {
  capabilities?: string[];
  name: string;
}) => {
  if (!capabilities?.length) return null;
  return (
    <Box>
      {capabilities.map((capability) => (
        <TagChip
          key={`${name}-${capability}`}
          sx={{ mr: 2, mt: 1 }}
          label={capability}
        />
      ))}
    </Box>
  );
};

/**
 * "Tag" means three different things around agents; this page shows two of them:
 *
 *  - capabilities — `AgentSummary.tags`, derived by the compiler from the agent's shape,
 *    recomputed on every deploy, read-only. Shown in the Capabilities column.
 *  - RBAC tags — key/value pairs authored by a user, stored separately, and the unit
 *    permissions are granted against. Fetched from /agent/tags. Shown in the Tags column.
 *  - A2A skill tags — what `AgentSummary.tags` feeds on the agent card, unrelated to either
 *    of the above beyond sharing the word.
 *
 * The row field is named rbacTags rather than tags so the distinction survives contact with
 * `AgentSummary.tags`, which sits right beside it and means something else entirely.
 */
type AgentRow = AgentSummary & { rbacTags: TagDto[] };

export default function AgentDefinitions() {
  const navigate = useNavigate();
  const { isTrialExpired } = useAuth();
  // Gates RBAC tags, not capabilities: named tagsEnabled to match the other definition
  // pages that read the same flag. Off in OSS, where there is no tag store.
  const tagsEnabled = featureFlags.isEnabled(FEATURES.TAG_VISIBILITY);
  const { data, isFetching, refetch } = useFetch<AgentSummary[]>("/agent/list");
  // Bulk user tags keyed by agent name. Gated on the tag feature: the endpoint is
  // orkes-only, and OSS has no tag store at all.
  const { data: rbacTagsByAgent, refetch: refetchTags } = useFetch<
    Record<string, TagDto[]>
  >("/agent/tags", { when: tagsEnabled });
  const { setMessage } = useContext(MessageContext);
  const [toastMessage, setToastMessage] = useState<PopoverMessage | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<AgentSummary | null>(null);
  const [agentToClone, setAgentToClone] = useState<AgentSummary | null>(null);
  const [agentToTag, setAgentToTag] = useState<AgentRow | null>(null);
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
          <NavLink
            data-cy="workflow-link"
            path={`${AGENT_DEFINITION_URL.BASE}/${encodeURIComponent(name.trim())}/${agent.version}`}
            id={`${name.trim()}-link-btn`}
          >
            {name.trim()}
          </NavLink>
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
        id: "workflow_tags",
        name: "tags",
        label: "Capabilities",
        searchable: true,
        searchableFunc: (capabilities: string[]) => (capabilities || []).join(", "),
        renderer: (capabilities: string[], row: AgentSummary) => (
          <CapabilityList capabilities={capabilities} name={row.name} />
        ),
        grow: 2,
        tooltip: "How the agent is composed, derived by the compiler",
      },
      ...(tagsEnabled
        ? ([
            {
              id: "agent_rbac_tags",
              name: "rbacTags",
              label: "Tags",
              searchable: true,
              searchableFunc: (tags: TagDto[]) =>
                (tags || []).map((t) => `${t.key}:${t.value}`).join(", "),
              renderer: (tags: TagDto[], row: AgentSummary) => (
                <TagList tags={tags || []} name={row.name} />
              ),
              grow: 2,
              tooltip: "Tags applied to this agent. Permissions can be granted on a tag.",
            },
          ] as LegacyColumn[])
        : []),
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
        renderer: (_: string, agent: AgentSummary) => (
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
            {tagsEnabled && (
              <Tooltip title="Add/Edit tags">
                <IconButton
                  id={`add-tags-${agent.name}-btn`}
                  disabled={isTrialExpired}
                  onClick={() => setAgentToTag(agent as AgentRow)}
                  size="small"
                >
                  <TagIcon size={20} />
                </IconButton>
              </Tooltip>
            )}
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
        ),
      },
    ],
    [isTrialExpired, navigate, tagsEnabled],
  );

  const handleFilterChange = useCallback(
    (filter?: FilterObjectItem) =>
      setFilterParam(filter ? JSON.stringify(filter) : ""),
    [setFilterParam],
  );

  const tableData = useMemo<AgentRow[]>(
    () =>
      (Array.isArray(data) ? data : []).map((agent) => ({
        ...agent,
        rbacTags: rbacTagsByAgent?.[agent.name] ?? [],
      })),
    [data, rbacTagsByAgent],
  );

  return (
    <>
      <Helmet>
        <title>Agent Definitions</title>
      </Helmet>
      {/* Agent tags live under ResourceType.AGENT. The dialog's default
          /metadata/{itemType}/{name}/tags path would write a WORKFLOW_DEF-typed
          row, which the AGENT-typed permission checks never see, so apiPath is
          pointed at the agent surface instead. */}
      {tagsEnabled && agentToTag && (
        <AddTagDialog
          open
          itemName={agentToTag.name}
          itemType="agent"
          tags={agentToTag.rbacTags || []}
          apiPath={`/agent/${encodeURIComponent(agentToTag.name)}/tags`}
          onClose={() => setAgentToTag(null)}
          onSuccess={() => {
            setAgentToTag(null);
            refetchTags();
          }}
        />
      )}
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
              "workflow_tags",
              ...(tagsEnabled ? ["agent_rbac_tags"] : []),
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
