import { Box, Tooltip } from "@mui/material";
import {
  CopySimple as CopyIcon,
  Trash as DeleteIcon,
  ArrowClockwise as RefreshIcon,
  Tag as TagIcon,
} from "@phosphor-icons/react";
import { Button, DataTable, IconButton, NavLink, Paper } from "components";
import { ColumnCustomType } from "components/DataTable/types";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import Header from "components/Header";
import NoDataComponent from "components/NoDataComponent";
import { SnackbarMessage } from "components/SnackbarMessage";
import TagChip from "components/TagChip";
import AddTagDialog, { TagDialogProps } from "components/tags/AddTagDialog";
import AddIcon from "components/v1/icons/AddIcon";
import { MessageContext } from "components/v1/layout/MessageContext";
import { useCallback, useContext, useMemo, useState } from "react";
import { Helmet } from "react-helmet";
import { useAuth } from "shared/auth";
import SectionContainer from "shared/SectionContainer";
import SectionHeader from "shared/SectionHeader";
import SectionHeaderActions from "shared/SectionHeaderActions";
import { colors } from "theme/tokens/variables";
import { TaskDto } from "types";
import { PopoverMessage } from "types/Messages";
import { TagDto } from "types/Tag";
import { NEW_TASK_DEF_URL, TASK_DEF_URL } from "utils/constants/route";
import { featureFlags, FEATURES } from "utils/flags";
import { parseErrorResponse } from "utils/helpers";
import useCustomPagination from "utils/hooks/useCustomPagination";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { logger } from "utils/logger";
import { useAction, useActionWithPath, useFetch } from "utils/query";
import { getSequentiallySuffix } from "utils/strings";
import { createSearchableTags } from "utils/utils";
import CloneDialog from "./dialog/CloneDialog";
import TagList from "components/v1/TagList";

const INTRO_CONTENT = `A **task definition** defines the task's default parameters, such as input/output schemas, timeouts, and retries. 
This provides reusability and modularity across workflows.

The task definition names match the name of your workers. 

These defaults can be overridden by the **Task Configuration** section in a workflow.

Read more:

* [Core Concepts: Tasks](https://orkes.io/content/core-concepts#task-definition)
* [Developer Guides: Tasks](https://orkes.io/content/developer-guides/tasks)
* [Task Definition API Docs](https://orkes.io/content/reference-docs/api/metadata/creating-task-definitions)
`;

export default function TaskDefinitions() {
  const [confirmDeleteName, setConfirmDeleteName] = useState("");
  const [showAddTagDialog, setShowAddTagDialog] = useState(false);
  const [addTagDialogData, setAddTagDialogData] =
    useState<TagDialogProps | null>(null);
  const [{ pageParam, searchParam }, { setSearchParam, handlePageChange }] =
    useCustomPagination();

  const [selectedTask, setSelectedTask] = useState<TaskDto | null>(null);
  const [toastMessage, setToastMessage] = useState<PopoverMessage | null>(null);

  const { setMessage } = useContext(MessageContext);
  const { isTrialExpired } = useAuth();

  const columns = useMemo(
    () => [
      {
        id: "name",
        name: "name",
        label: "Task name",
        renderer: (name: string) => (
          <NavLink path={`${TASK_DEF_URL.BASE}/${encodeURIComponent(name)}`}>
            {name}
          </NavLink>
        ),
        tooltip: "Task name",
      },
      {
        id: "executable",
        name: "executable",
        label: "Executable?",
        renderer: (executable: boolean) => (
          <TagChip
            style={{
              background: executable ? colors.successTag : colors.errorTag,
            }}
            sx={{ mr: 2 }}
            label={executable ? "Yes" : "No"}
          />
        ),
        tooltip:
          "Tasks marked as Yes are available for you to execute. If you need access to execute any other task, please contact the task owner or your Administrator.",
      },
      {
        id: "description",
        name: "description",
        label: "Description",
        grow: 2,
        tooltip: "Task description",
      },
      {
        id: "createTime",
        name: "createTime",
        label: "Created time",
        type: ColumnCustomType.DATE,
        tooltip: "Task created time",
      },
      {
        id: "ownerEmail",
        name: "ownerEmail",
        label: "Owner email",
        tooltip: "Task owner email",
      },
      {
        id: "inputKeys",
        name: "inputKeys",
        label: "Input keys",
        type: ColumnCustomType.JSON,
        sortable: false,
        tooltip: "Task input keys",
      },
      {
        id: "outputKeys",
        name: "outputKeys",
        label: "Output keys",
        type: ColumnCustomType.JSON,
        sortable: false,
        tooltip: "Task output keys",
      },
      {
        id: "timeoutPolicy",
        name: "timeoutPolicy",
        label: "Timeout policy",
        grow: 0.5,
        tooltip: "Task timeout policy",
      },
      {
        id: "timeoutSeconds",
        name: "timeoutSeconds",
        label: "Timeout seconds",
        grow: 0.5,
        tooltip: "Task timeout seconds",
      },
      {
        id: "retryCount",
        name: "retryCount",
        label: "Retry count",
        grow: 0.5,
        tooltip: "Task retry count",
      },
      {
        id: "retryLogic",
        name: "retryLogic",
        label: "Retry logic",
        tooltip: "Task retry logic",
      },
      {
        id: "retryDelaySeconds",
        name: "retryDelaySeconds",
        label: "Retry delay seconds",
        grow: 0.5,
        tooltip: "Task retry delay seconds",
      },
      {
        id: "responseTimeoutSeconds",
        name: "responseTimeoutSeconds",
        label: "Response timeout seconds",
        grow: 0.5,
        tooltip: "Task response timeout seconds",
      },
      {
        id: "inputTemplate",
        name: "inputTemplate",
        label: "Input template",
        type: ColumnCustomType.JSON,
        sortable: false,
        tooltip: "Task input template",
      },
      {
        id: "rateLimitPerFrequency",
        name: "rateLimitPerFrequency",
        label: "Rate limit per freq",
        grow: 0.5,
        tooltip: "Task rate limit per frequency",
      },
      {
        id: "rateLimitFrequencyInSeconds",
        name: "rateLimitFrequencyInSeconds",
        label: "Rate limit freq in seconds",
        grow: 0.5,
        tooltip: "Task rate limit frequency in seconds",
      },
      {
        id: "tags",
        name: "tags",
        label: "Tags",
        searchable: true,
        tooltip: "Task tags",
        searchableFunc: (tags: TagDto[]) => createSearchableTags(tags),
        renderer: (tags: TagDto[], row: TaskDto) => (
          <TagList tags={tags} name={row?.name} />
        ),
        grow: 2,
      },
      {
        id: "actions",
        name: "name",
        label: "Actions",
        sortable: false,
        searchable: false,
        grow: 0.5,
        minWidth: "130px",
        tooltip: "Actions that can be performed on the task definition",
        renderer: (name: string, taskRowData: TaskDto) => (
          <Box sx={{ display: "flex", justifyContent: "space-evenly", gap: 2 }}>
            <Tooltip title={"Clone task definition"}>
              <IconButton
                id={`clone-${name}-btn`}
                onClick={() => setSelectedTask(taskRowData)}
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
                id={`add-tag-${name}-btn`}
                disabled={isTrialExpired}
                onClick={() => {
                  setAddTagDialogData({
                    apiPath: "",
                    onClose(): void {},
                    onSuccess(): void {},
                    tags: taskRowData.tags || [],
                    itemName: taskRowData.name,
                    itemType: "task",
                  } as TagDialogProps);
                  setShowAddTagDialog(true);
                }}
                size="small"
              >
                <TagIcon />
              </IconButton>
            </Tooltip>

            <Tooltip title={"Delete task definition"}>
              <IconButton
                id={`delete-${name}-btn`}
                disabled={isTrialExpired}
                onClick={() => {
                  setConfirmDeleteName(name);
                }}
                size="small"
                color="error"
                sx={{
                  whiteSpace: "nowrap",
                }}
              >
                <DeleteIcon size={20} />
              </IconButton>
            </Tooltip>
          </Box>
        ),
      },
    ],
    [isTrialExpired],
  );

  const taskVisibility = featureFlags.getValue(
    FEATURES.TASK_VISIBILITY,
    "READ",
  );
  const pushHistory = usePushHistory();
  const {
    data: visibilityData,
    isFetching,
    refetch,
  } = useFetch(`/metadata/taskdefs?access=${taskVisibility}&metadata=true`);

  const {
    data: readonlyData,
    isFetching: isReadonlyDataFetching,
    refetch: refetchReadonlyData,
  } = useFetch(`/metadata/taskdefs?access=READ&metadata=true`);

  const refetchData = () => {
    refetch();
    refetchReadonlyData();
  };

  const deleteTaskDefinitionAction = useActionWithPath({
    onSuccess: () => {
      refetchData();
    },
    onError: async (err: any) => {
      const message = await err?.json();
      setMessage({
        text: message?.message,
        severity: "error",
      });
      logger.error(err);
      refetchData();
    },
  });

  const tableData = useMemo<TaskDto[]>(
    () =>
      readonlyData && visibilityData
        ? readonlyData.reduce((result: TaskDto[], currentItem: TaskDto) => {
            const executable =
              visibilityData.findIndex(
                (item: TaskDto) => item.name === currentItem.name,
              ) > -1;
            result.push({
              createTime: !currentItem.createTime ? 0 : currentItem.createTime,
              ...currentItem,
              executable,
            });

            return result;
          }, [])
        : [],
    [visibilityData, readonlyData],
  );

  const handleSearchTermChange = useCallback(
    (searchTerm: string) => {
      setSearchParam(searchTerm);
    },
    [setSearchParam],
  );

  const handleClickDefineTask = () => {
    pushHistory(NEW_TASK_DEF_URL);
  };

  const taskNameList: string[] = useMemo(
    () => (tableData ? tableData.map((task: TaskDto) => task.name) : []),
    [tableData],
  );

  const { mutate: saveTask, isLoading: isSavingTask } = useAction(
    "/metadata/taskdefs",
    "post",
    {
      onSuccess: () => {
        refetchData();
        setSelectedTask(null);
        setToastMessage({
          text: "Task cloned successfully",
          severity: "success",
        });
      },
      onError: async (error: Response) => {
        logger.error(error);
        const errorMessage = await parseErrorResponse({
          response: error,
          module: "task",
          operation: "cloning",
        });
        setToastMessage({
          text: errorMessage,
          severity: "error",
        });
      },
    },
  );

  return (
    <>
      <Helmet>
        <title>Task Definitions</title>
      </Helmet>

      {selectedTask && (
        <CloneDialog
          name={
            getSequentiallySuffix({
              name: selectedTask.name,
              refNames: taskNameList,
            }).name
          }
          namesList={taskNameList ?? []}
          onClose={() => setSelectedTask(null)}
          onSuccess={({ name }) => {
            const newTaskDefinition = { ...selectedTask, name };
            // @ts-ignore
            saveTask({
              body: JSON.stringify([newTaskDefinition]),
            });
          }}
          isFetching={isSavingTask}
          title="Clone Task Confirmation"
          id="task-name-field"
          label="Task name"
        />
      )}

      <AddTagDialog
        open={showAddTagDialog && !!addTagDialogData}
        tags={addTagDialogData?.tags || []}
        itemName={addTagDialogData?.itemName}
        itemType={addTagDialogData?.itemType}
        onClose={() => {
          setShowAddTagDialog(false);
          setAddTagDialogData(null);
        }}
        onSuccess={() => {
          setShowAddTagDialog(false);
          refetchData();
        }}
      />

      {confirmDeleteName && (
        <ConfirmChoiceDialog
          handleConfirmationValue={(selectedChoice) => {
            if (selectedChoice && confirmDeleteName) {
              // @ts-ignore
              deleteTaskDefinitionAction.mutate({
                method: "delete",
                path: `/metadata/taskdefs/${encodeURIComponent(
                  confirmDeleteName,
                )}`,
              });
            }
            setConfirmDeleteName("");
          }}
          message={
            <>
              Are you sure you want to delete{" "}
              <strong style={{ color: "red" }}>{confirmDeleteName}</strong> task
              definition? This cannot be undone.
              <div style={{ marginTop: "15px" }}>
                Please type <strong>{confirmDeleteName}</strong> to confirm.
              </div>
            </>
          }
          header={"Deletion Confirmation"}
          isInputConfirmation
          valueToBeDeleted={confirmDeleteName}
        />
      )}
      <SectionHeader
        title="Task Definitions"
        _deprecate_marginTop={0}
        actions={
          <SectionHeaderActions
            buttons={[
              {
                id: "define-task-btn",
                label: "Define task",
                onClick: () => pushHistory(NEW_TASK_DEF_URL),
                startIcon: <AddIcon />,
              },
            ]}
          />
        }
      />
      <SectionContainer>
        {/*@ts-ignore*/}
        <Paper variant="outlined">
          <Header loading={isFetching || isReadonlyDataFetching} />
          {tableData && (
            <>
              <DataTable
                localStorageKey="tasksTable"
                quickSearchEnabled
                quickSearchPlaceholder="Search task definitions"
                searchTerm={searchParam}
                onSearchTermChange={handleSearchTermChange}
                defaultShowColumns={[
                  "name",
                  "executable",
                  "description",
                  "tags",
                  "ownerEmail",
                  "timeoutPolicy",
                  "retryCount",
                  "executions_link",
                  "timeoutSeconds",
                  "responseTimeoutSeconds",
                  "actions",
                ]}
                keyField="name"
                data={tableData}
                columns={columns}
                customActions={[
                  <Tooltip
                    title="Refresh Task Definitions"
                    key="refresh-task-definition"
                  >
                    <Button
                      variant="text"
                      color="inherit"
                      size="small"
                      startIcon={<RefreshIcon />}
                      key="refresh"
                      onClick={refetchData}
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
                      title="Task Definition"
                      description={INTRO_CONTENT}
                      buttonText="Define a Task"
                      buttonHandler={handleClickDefineTask}
                      disableButton={isTrialExpired}
                    />
                  ) : (
                    <NoDataComponent
                      title="Empty"
                      titleBg={colors.warningTag}
                      description="I'm sorry that search didn't find any matches. Please try different filters."
                      buttonText="Clear search"
                      buttonHandler={() => handleSearchTermChange("")}
                    />
                  )
                }
              />
            </>
          )}
        </Paper>
      </SectionContainer>
      {toastMessage && (
        <SnackbarMessage
          autoHideDuration={3000}
          id="task-definition-toast-message"
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
