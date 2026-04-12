import { Box, Tooltip } from "@mui/material";
import {
  CopySimple as CopyIcon,
  Trash as DeleteIcon,
  ArrowClockwise as RefreshIcon,
  Tag as TagIcon,
} from "@phosphor-icons/react";
import { Button, DataTable, IconButton, NavLink, Paper } from "components";
import { FilterObjectItem } from "components/ui/DataTable/state";
import { ColumnCustomType, LegacyColumn } from "components/ui/DataTable/types";
import Header from "components/ui/Header";
import NoDataComponent from "components/ui/NoDataComponent";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import AddTagDialog, {
  TagDialogProps,
} from "components/features/tags/AddTagDialog";
import TagList from "components/ui/TagList";
import PlayIcon from "components/icons/PlayIcon";
import { MessageContext } from "components/providers/messageContext";
import SplitWorkflowDefinitionButton from "pages/executions/SplitWorkflowDefinitionButton/SplitWorkflowDefinitionButton";
import { removeDeletedWorkflow } from "pages/runWorkflow/runWorkflowUtils";
import { useCallback, useContext, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { UseQueryResult } from "react-query";
import { useNavigate } from "react-router";
import SectionContainer from "components/ui/layout/SectionContainer";
import SectionHeader from "components/layout/SectionHeader";
import SectionHeaderActions from "components/ui/layout/SectionHeaderActions";
import { useAuth } from "components/features/auth";
import { colors } from "theme/tokens/variables";
import { PopoverMessage } from "types/Messages";
import { TagDto } from "types/Tag";
import { WorkflowDef } from "types/WorkflowDef";
import {
  RUN_WORKFLOW_URL,
  WORKFLOW_DEFINITION_URL,
} from "utils/constants/route";
import { featureFlags, FEATURES } from "utils/flags";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { logger } from "utils/logger";
import { useActionWithPath, useWorkflowDefs } from "utils/query";
import { createSearchableTags, tryToJson } from "utils/utils";
import { getUniqueWorkflows } from "utils/workflow";
import CloneWorkflowDialog from "./dialog/CloneWorkflowDialog";

const INTRO_CONTENT = `A **workflow definition** is a blueprint that defines the sequence of tasks, their dependencies, and how data flows between them.

Workflows can be versioned, tagged, and reused across your applications. They provide a visual and programmatic way to orchestrate complex business processes.

Read more:

* [Core Concepts: Workflows](https://orkes.io/content/core-concepts#workflow-definition)
* [Developer Guides: Workflows](https://orkes.io/content/developer-guides/workflows)
* [Workflow API Reference](https://orkes.io/content/reference-docs/api/workflow)

Browse our templates to get started with easy examples!
`;

export default function WorkflowDefinitions() {
  const navigate = useNavigate();
  const { isTrialExpired } = useAuth();

  const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);
  const { data, isFetching, refetch }: UseQueryResult<WorkflowDef[]> =
    useWorkflowDefs();
  const [showAddTagDialog, setShowAddTagDialog] = useState(false);
  const [addTagDialogData, setAddTagDialogData] =
    useState<TagDialogProps | null>(null);

  const [selectedWorkflowWithAction, setSelectedWorkflowWithAction] = useState<{
    selectedWorkflow: WorkflowDef | null;
    action: string;
  }>({
    selectedWorkflow: null,
    action: "",
  });
  const [toastMessage, setToastMessage] = useState<PopoverMessage | null>(null);

  const { setMessage } = useContext(MessageContext);
  const pushHistory = usePushHistory();
  const [
    { filterParam, pageParam, searchParam },
    { setFilterParam, setSearchParam, handlePageChange },
  ] = useCustomPagination();
  const [confirmDelete, setConfirmDelete] = useState<{
    confirmDelete: boolean;
    workflowName: string;
    workflowVersion: number;
  } | null>(null);
  const filterObj =
    filterParam === "" ? undefined : tryToJson<FilterObjectItem>(filterParam);

  const deleteWorkflowVersionAction = useActionWithPath({
    onSuccess: () => {
      if (confirmDelete?.workflowName) {
        removeDeletedWorkflow(
          encodeURIComponent(confirmDelete?.workflowName),
          confirmDelete?.workflowVersion,
        );
      }

      refetch();
    },
    onError: (err: Error) => {
      setMessage({
        severity: "error",
        text: "Failed to delete workflow",
      });
      logger.error(err);
      refetch();
    },
  });

  const columns = useMemo<LegacyColumn[]>(
    () => [
      {
        id: "workflow_name",
        name: "name",
        label: "Workflow name",
        renderer: (val: string) => {
          return (
            <NavLink
              data-cy="workflow-link"
              path={`${WORKFLOW_DEFINITION_URL.BASE}/${encodeURIComponent(
                val.trim(),
              )}`}
              id={`${val.trim()}-link-btn`}
            >
              {val.trim()}
            </NavLink>
          );
        },
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
        label: "Tags",
        searchable: true,
        searchableFunc: (tags: TagDto[]) => createSearchableTags(tags),
        renderer: (tags: TagDto[], row: WorkflowDef) => (
          <TagList tags={tags} name={row?.name} />
        ),
        grow: 2,
        tooltip: "The tags associated with the workflow",
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
            path={`/executions?workflowType=${encodeURIComponent(name.trim())}`}
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
        renderer: (name: string, workflowRowData: WorkflowDef) => {
          return (
            <Box style={{ display: "flex", justifyContent: "space-evenly" }}>
              <Tooltip title={"Run workflow"}>
                <IconButton
                  id={`run-${workflowRowData.name}-btn`}
                  disabled={isTrialExpired}
                  onClick={() => {
                    navigate("/runWorkflow", {
                      state: {
                        execution: {
                          workflowName: workflowRowData.name,
                          workflowVersion: workflowRowData.version,
                          input: workflowRowData?.inputParameters
                            ? Object.fromEntries(
                                workflowRowData.inputParameters.map((key) => [
                                  key,
                                  "",
                                ]),
                              )
                            : {},
                        },
                      },
                    });
                  }}
                  size="small"
                  sx={{
                    whiteSpace: "nowrap",
                  }}
                >
                  <PlayIcon size={22} />
                </IconButton>
              </Tooltip>

              <Tooltip title={"Clone Workflow"}>
                <IconButton
                  onClick={() =>
                    setSelectedWorkflowWithAction({
                      selectedWorkflow: workflowRowData,
                      action: "clone",
                    })
                  }
                  disabled={isTrialExpired}
                  size="small"
                  sx={{
                    whiteSpace: "nowrap",
                  }}
                >
                  <CopyIcon size={20} />
                </IconButton>
              </Tooltip>
              <Tooltip title={"Add/Edit tags"}>
                <IconButton
                  id={`add-tags-${workflowRowData.name}-btn`}
                  disabled={isTrialExpired}
                  onClick={() => {
                    setAddTagDialogData({
                      tags: workflowRowData.tags || [],
                      itemName: workflowRowData.name,
                      itemType: "workflow",
                    } as TagDialogProps);
                    setShowAddTagDialog(true);
                  }}
                  size="small"
                >
                  <TagIcon size={20} />
                </IconButton>
              </Tooltip>

              <Tooltip title={"Delete workflow"}>
                <IconButton
                  id={`delete-${workflowRowData.name}-btn`}
                  disabled={isTrialExpired}
                  onClick={() => {
                    const selectedData = data?.find((x) => x.name === name);
                    if (selectedData) {
                      setConfirmDelete({
                        confirmDelete: true,
                        workflowName: selectedData.name,
                        workflowVersion: selectedData.version,
                      });
                    }
                  }}
                  size="small"
                  sx={{
                    whiteSpace: "nowrap",
                  }}
                >
                  <DeleteIcon size={20} />
                </IconButton>
              </Tooltip>
            </Box>
          );
        },
      },
    ],
    [data, navigate, isTrialExpired],
  );

  const handleFilterChange = useCallback(
    (obj?: FilterObjectItem) => {
      if (obj) {
        setFilterParam(JSON.stringify(obj));
      } else {
        setFilterParam("");
      }
    },
    [setFilterParam],
  );

  const workflows = useMemo(() => {
    // Extract latest versions only
    if (data) {
      return getUniqueWorkflows(data);
    }
  }, [data]);

  const handleClickBrowseTemplates = () => {
    pushHistory(isPlayground ? "/" : WORKFLOW_DEFINITION_URL.NEW);
  };

  return (
    <>
      <Helmet>
        <title>Workflow Definitions</title>
      </Helmet>

      {selectedWorkflowWithAction &&
        selectedWorkflowWithAction?.selectedWorkflow &&
        selectedWorkflowWithAction?.action === "clone" && (
          <CloneWorkflowDialog
            onClose={() =>
              setSelectedWorkflowWithAction({
                selectedWorkflow: null,
                action: "",
              })
            }
            onSuccess={() => {
              setSelectedWorkflowWithAction({
                selectedWorkflow: null,
                action: "",
              });
              refetch();
              setToastMessage({
                text: "Workflow cloned successfully",
                severity: "success",
              });
            }}
            selectedWorkflow={selectedWorkflowWithAction?.selectedWorkflow}
            workflowList={data ?? []}
          />
        )}

      <AddTagDialog
        open={showAddTagDialog && !!addTagDialogData}
        tags={addTagDialogData?.tags || []}
        itemType={addTagDialogData?.itemType}
        itemName={addTagDialogData?.itemName}
        onClose={() => {
          setShowAddTagDialog(false);
          setAddTagDialogData(null);
        }}
        onSuccess={() => {
          setShowAddTagDialog(false);
          setAddTagDialogData(null);
          refetch();
        }}
      />

      {confirmDelete && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(selectedChoice) => {
            if (selectedChoice) {
              // @ts-ignore
              deleteWorkflowVersionAction.mutate({
                method: "delete",
                path: `/metadata/workflow/${encodeURIComponent(
                  confirmDelete.workflowName,
                )}/${confirmDelete.workflowVersion}`,
              });
            }
            setConfirmDelete(null);
          }}
          message={
            <>
              Are you sure you want to delete{" "}
              <strong style={{ color: "red" }}>
                {confirmDelete.workflowName}
              </strong>{" "}
              workflow definition? This cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type <strong>{confirmDelete.workflowName}</strong> to
                confirm.
              </div>
            </>
          }
          header={"Deletion confirmation"}
          isInputConfirmation
          valueToBeDeleted={confirmDelete.workflowName}
        />
      )}
      <SectionHeader
        _deprecate_marginTop={0}
        title="Workflow Definitions"
        actions={
          <SectionHeaderActions
            buttons={[
              {
                label: "Run workflow",
                color: "secondary",
                onClick: () => pushHistory(RUN_WORKFLOW_URL),
                startIcon: <PlayIcon />,
              },
              {
                customButtonElement: <SplitWorkflowDefinitionButton />,
              },
            ]}
          />
        }
      />
      <SectionContainer>
        <Paper id="workflow-definitions-table-wrapper" variant="outlined">
          <Header loading={isFetching} />
          {workflows && (
            <DataTable
              localStorageKey="workflowsTable"
              quickSearchEnabled
              quickSearchPlaceholder="Search workflow definitions"
              searchTerm={searchParam}
              onSearchTermChange={setSearchParam}
              defaultShowColumns={[
                "workflow_name",
                "workflow_description",
                "workflow_tags",
                "latest_version",
                "create_time",
                "owner_email",
                "executions_link",
                "actions",
              ]}
              keyField="name"
              onFilterChange={handleFilterChange}
              initialFilterObj={filterObj}
              data={workflows}
              columns={columns}
              filterByTags
              customActions={[
                <Tooltip
                  title="Refresh workflow definitions"
                  key={"rfrshWdefs"}
                >
                  <Button
                    variant="text"
                    color="inherit"
                    size="small"
                    startIcon={<RefreshIcon />}
                    key="refresh"
                    onClick={refetch as () => void}
                  >
                    Refresh
                  </Button>
                </Tooltip>,
              ]}
              onChangePage={handlePageChange}
              paginationDefaultPage={pageParam ? Number(pageParam) : 1}
              noDataComponent={
                searchParam === "" ? (
                  <NoDataComponent
                    title="Workflow Definition"
                    description={INTRO_CONTENT}
                    buttonText={
                      isPlayground ? "Browse Templates" : "Define a Workflow"
                    }
                    buttonHandler={handleClickBrowseTemplates}
                  />
                ) : (
                  <NoDataComponent
                    title="Empty"
                    titleBg={colors.warningTag}
                    description="I'm sorry that search didn't find any matches. Please try different filters."
                    buttonText="Clear search"
                    buttonHandler={() => setSearchParam("")}
                  />
                )
              }
            />
          )}
        </Paper>
      </SectionContainer>
      {toastMessage && (
        <SnackbarMessage
          autoHideDuration={3000}
          id="workflow-definitions-toast-message"
          message={toastMessage.text}
          severity={toastMessage.severity}
          onDismiss={() => {
            setToastMessage(null);
          }}
        />
      )}
    </>
  );
}
