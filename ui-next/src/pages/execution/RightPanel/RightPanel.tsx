import { Box, Paper } from "@mui/material";
import { ArrowCounterClockwise, X as CloseIcon } from "@phosphor-icons/react";
import {
  Button,
  DropdownButton,
  Heading,
  IconButton,
  ReactJson,
  Tab,
  Tabs,
} from "components";
import ClipboardCopy from "components/ui/ClipboardCopy";
import { dowhileHasAllIterationsInOutput } from "components/features/flow/components/shapes/TaskCard/helpers";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import StatusBadge from "components/StatusBadge";
import ConductorTooltip from "components/ui/ConductorTooltip";
import _nth from "lodash/nth";
import { FunctionComponent, useMemo } from "react";
import { useContainerQuery } from "react-container-query";
import { colors } from "theme/tokens/variables";
import { TaskType } from "types/common";
import {
  DoWhileSelection,
  ExecutionTask,
  WorkflowExecutionStatus,
} from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { featureFlags, FEATURES } from "utils/flags";
import { usePushHistory } from "utils/hooks/usePushHistory";
import { ActorRef } from "xstate";
import { UpdateTaskStatusForm } from "..";
import {
  DEFINITION_TAB,
  INPUT_TAB,
  JSON_TAB,
  LOGS_TAB,
  OUTPUT_TAB,
  SUMMARY_TAB,
} from "../state/constants";
import TaskLogs from "../TaskLogs";
import TaskSummary from "../TaskSummary";
import { RightPanelContextEventTypes, RightPanelEvents } from "./state";
import { useRightPanelActor } from "./state/hook";
import { SummaryTask } from "./SummaryTask";

const executionTaskHeaderContainerQuery = {
  small: {
    maxWidth: 699,
  },
  large: {
    minWidth: 700,
  },
};

const rerunFromForkAndDowhileTasksEnabled = featureFlags.isEnabled(
  FEATURES.ENABLE_RERUN_FROM_FORK_AND_DOWHILE_TASKS,
);

interface SecondaryActionsProps {
  selectedTask: ExecutionTask;
  containerQueryState: any;
  dynamicForkInstances: any;
}
interface LabelRendererProps {
  iterationTask: any;
  isIteration?: boolean;
  hideTaskId?: boolean;
}

interface RightPanelProps {
  rightPanelActor: ActorRef<RightPanelEvents>;
  workflowName: string;
  workflowStatus: string;
  doWhileSelection?: DoWhileSelection[];
}

const SecondaryActions = ({
  selectedTask,
  containerQueryState,
  dynamicForkInstances,
}: SecondaryActionsProps) => {
  const navigate = usePushHistory();
  return selectedTask?.workflowTask?.type === "SIMPLE" ? ( // Within  dynamic forks. Tasks cant be simple. the task name is used as the type. since the name is generated
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        gap: 2,
        alignItems: containerQueryState["small"] ? "start" : "end",
      }}
    >
      <Box
        sx={{
          display: "flex",
          gap: 2,
          flexGrow: 0,
          flexShrink: 0,
        }}
      >
        <Button
          color="secondary"
          size="small"
          onClick={() => {
            navigate(`/taskDef/${encodeURIComponent(selectedTask.taskType)}`);
          }}
        >
          Go to definition
        </Button>
      </Box>
    </Box>
  ) : (
    dynamicForkInstances
  );
};

const LabelRenderer = ({
  iterationTask,
  isIteration,
  hideTaskId = false,
}: LabelRendererProps) => {
  const textLabel = isIteration
    ? `Iteration ${iterationTask.iteration} ${
        iterationTask?.retryCount > 0
          ? " - retry attempt " + iterationTask.retryCount
          : ""
      }`
    : `Attempt #${iterationTask.retryCount}`;
  return (
    <Box sx={{ marginRight: "auto" }}>
      {dropdownIcon(iterationTask.status)}{" "}
      {`${textLabel} ${hideTaskId ? "" : `- ${iterationTask.taskId}`}`}
    </Box>
  );
};
function getOrderedIterationKeys(
  outputData: Record<string, any>,
  selectedTask: { iteration?: number },
): number[] {
  // Extract keys that are numbers
  const keys = Object.keys(outputData)
    .map(Number)
    .filter((k) => !isNaN(k));
  // Sort numerically
  keys.sort((a, b) => b - a);

  // Prepend the selected iteration if it is not in the list
  if (
    typeof selectedTask.iteration === "number" &&
    !keys.includes(selectedTask.iteration)
  ) {
    return [selectedTask.iteration, ...keys];
  }
  return keys;
}

const DoWhileIteration = ({
  selectedTask,
  handleSelectDoWhileIteration,
  doWhileSelection,
}: {
  selectedTask: ExecutionTask;
  handleSelectDoWhileIteration: (data: DoWhileSelection) => void;
  doWhileSelection?: DoWhileSelection[];
}) => {
  const isTaskProcessing = [
    TaskStatus.PENDING,
    TaskStatus.SCHEDULED,
    TaskStatus.IN_PROGRESS,
  ].includes(selectedTask.status);
  const retryIterationOptions = getOrderedIterationKeys(
    selectedTask?.outputData ?? {},
    selectedTask,
  );

  const currentIteration = _nth(
    doWhileSelection?.filter(
      (item) =>
        item.doWhileTaskReferenceName === selectedTask?.referenceTaskName,
    ),
    0,
  )?.selectedIteration;

  const dropIconRender = (option: number) => {
    const completedIterations = Object.keys(selectedTask?.outputData ?? {});
    if (completedIterations.includes(option.toString())) {
      return dropdownIcon("COMPLETED");
    }
    return dropdownIcon(selectedTask.status);
  };

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "flex-start",
      }}
    >
      <Box pt={2}>
        <DropdownButton
          buttonProps={{
            color: "secondary",
            variant: "outlined",
            size: "small",
            style: {
              color: colors.gray04,
              fontSize: "9pt",
              minWidth: "300px",
            },
          }}
          options={
            retryIterationOptions?.map((option: number) => {
              return {
                label: (
                  <Box sx={{ marginRight: "auto", minWidth: "300px" }}>
                    {dropIconRender(option)} {`iteration ${option}`}
                  </Box>
                ),
                handler: () =>
                  handleSelectDoWhileIteration({
                    doWhileTaskReferenceName: selectedTask?.referenceTaskName,
                    selectedIteration: option,
                  }),
              };
            }) ?? []
          }
        >
          <Box
            sx={{ marginRight: "auto", minWidth: "300px", textAlign: "left" }}
          >
            {currentIteration != null ? (
              <>
                {dropIconRender(currentIteration)}{" "}
                {`iteration ${currentIteration}`}
              </>
            ) : null}
          </Box>
        </DropdownButton>
        {selectedTask?.inputData?.keepLastN != null ? (
          <ConductorTooltip
            title=""
            content={`keepLastN is set to ${selectedTask?.inputData?.keepLastN}`}
            placement="top"
          >
            <img
              alt="info"
              src="/icons/info-icon.svg"
              style={{ paddingLeft: "3px" }}
            />
          </ConductorTooltip>
        ) : !isTaskProcessing &&
          !dowhileHasAllIterationsInOutput(selectedTask?.outputData ?? {}) ? (
          <ConductorTooltip
            title=""
            content={`The workflow has been summarized from its original size.`}
            placement="top"
          >
            <img
              alt="info"
              src="/icons/info-icon.svg"
              style={{ paddingLeft: "3px" }}
            />
          </ConductorTooltip>
        ) : null}
      </Box>
    </Box>
  );
};

export const RightPanel: FunctionComponent<RightPanelProps> = ({
  rightPanelActor,
  workflowName,
  workflowStatus,
  doWhileSelection,
}) => {
  const [containerQueryState, containerRef] = useContainerQuery(
    executionTaskHeaderContainerQuery,
    { width: 100, height: 100 },
  );

  const [
    {
      selectedTask,
      isIteration,
      retryIterationOptions,
      errorMessage,
      currentTab,
      maybeSiblings,
      isReRunFromTaskInProgress,
    },
    {
      handleChangeTaskStatus: onChangeTaskStatus,
      handleClosePanel: onClosePanel,
      handleReRunRequest,
      clearErrorMessage,
      handleSelectTask,
      handleSelectDoWhileIteration,
    },
  ] = useRightPanelActor(rightPanelActor);

  const dfOptions: ExecutionTask[] = maybeSiblings;

  const maybeStatusForm = useMemo(
    () =>
      selectedTask?.status &&
      [TaskStatus.IN_PROGRESS, TaskStatus.SCHEDULED].includes(
        selectedTask.status,
      ) ? (
        <UpdateTaskStatusForm
          onConfirm={onChangeTaskStatus!}
          key={selectedTask?.referenceTaskName}
        />
      ) : null,
    [selectedTask, onChangeTaskStatus],
  );

  const maybeRerunTask = useMemo(() => {
    if (workflowStatus !== WorkflowExecutionStatus.PAUSED) {
      return (
        <Box mt={2}>
          <Button
            startIcon={<ArrowCounterClockwise />}
            size="small"
            onClick={handleReRunRequest}
            id="re-run-task-btn"
          >
            Re-Run from Task
          </Button>
        </Box>
      );
    }
    return null;
  }, [handleReRunRequest, workflowStatus]);

  const changeCurrentTab = (tab: number) => {
    rightPanelActor.send({
      type: RightPanelContextEventTypes.CHANGE_CURRENT_TAB,
      currentTab: tab,
    });
  };

  // If the summary task is selected just show a small summary
  if (selectedTask?.taskType === "TASK_SUMMARY")
    return <SummaryTask selectedTask={selectedTask} onClose={onClosePanel!} />;

  return !selectedTask ? null : (
    <Paper square elevation={0} id="execution-page-right-panel">
      {errorMessage && (
        <SnackbarMessage
          message={errorMessage}
          severity="error"
          onDismiss={clearErrorMessage}
        />
      )}
      <Box
        sx={{
          display: "flex",
        }}
      >
        <Box
          sx={{
            display: "flex",
            alignItems: "start",
            paddingTop: 2,
            paddingLeft: 1,
            margin: 0,
          }}
        >
          <IconButton
            id="execution-righ-panel-close-btn"
            color="secondary"
            size="small"
            aria-label="Close button"
            onClick={onClosePanel}
            sx={{
              opacity: 0.5,
            }}
          >
            <CloseIcon />
          </IconButton>
        </Box>
        <Box
          sx={{
            width: "100%",
          }}
        >
          <Box
            ref={containerRef}
            sx={{
              width: "100%",
              padding: 3,
              gap: 3,
              display: "flex",
              flexWrap: "wrap",
              justifyContent: "space-between",
              backgroundColor: (theme) =>
                theme.palette?.mode === "dark" ? colors.black : colors.white,
            }}
          >
            <Box>
              <Box
                sx={{
                  display: "flex",
                  paddingRight: 2,
                  marginBottom: 2,
                  width: "100%",
                  gap: 2,
                }}
              >
                <Heading
                  fontWeight={700}
                  fontSize={20}
                  level={1}
                  sx={{
                    textOverflow: "ellipsis",
                    overflow: "hidden",
                    whiteSpace: "nowrap",
                  }}
                >
                  {selectedTask.workflowTask.name}
                </Heading>
                <StatusBadge status={selectedTask?.status} />
              </Box>
              {selectedTask?.status === "PENDING" ? null : (
                <Box
                  sx={{
                    fontSize: 14,
                    width: "100%",
                  }}
                >
                  <ClipboardCopy value={selectedTask?.taskId || ""}>
                    <Box
                      sx={{
                        textOverflow: "ellipsis",
                        overflow: "hidden",
                        whiteSpace: "nowrap",
                      }}
                      id="right-panel-task-id"
                    >
                      {selectedTask.taskId}
                    </Box>
                  </ClipboardCopy>
                </Box>
              )}
              <Box>
                {retryIterationOptions && retryIterationOptions?.length > 1 ? (
                  <Box
                    sx={{
                      display: "flex",
                      justifyContent: "flex-start",
                      flexDirection: containerQueryState["small"]
                        ? "column"
                        : "row",
                    }}
                  >
                    <Box pt={2}>
                      <DropdownButton
                        buttonProps={{
                          id: "iteration-dropdown-btn",
                          color: "secondary",
                          variant: "outlined",
                          size: "small",
                          style: {
                            color: colors.gray04,
                            fontSize: "9pt",
                            minWidth: "300px",
                          },
                        }}
                        options={
                          retryIterationOptions.map((option: ExecutionTask) => {
                            return {
                              label: (
                                <LabelRenderer
                                  isIteration={isIteration}
                                  iterationTask={option}
                                />
                              ),
                              handler: () => handleSelectTask(option),
                            };
                          }) ?? []
                        }
                      >
                        <LabelRenderer
                          isIteration={isIteration}
                          iterationTask={selectedTask}
                          hideTaskId
                        />
                      </DropdownButton>
                    </Box>
                  </Box>
                ) : null}
                {selectedTask?.taskType === TaskType.DO_WHILE && (
                  <DoWhileIteration
                    selectedTask={selectedTask}
                    doWhileSelection={doWhileSelection}
                    handleSelectDoWhileIteration={handleSelectDoWhileIteration}
                  />
                )}
              </Box>
              {((selectedTask?.workflowTask?.type !== TaskType.DO_WHILE &&
                selectedTask?.workflowTask?.type !== TaskType.FORK_JOIN) ||
                rerunFromForkAndDowhileTasksEnabled) && (
                <Box>{maybeRerunTask}</Box>
              )}
            </Box>
            <Box
              sx={{
                width: "fit-content",
                height: "fit-content",
                display: "flex",
                flexGrow: 0,
                flexShrink: 0,
                justifyContent: containerQueryState["small"] ? "start" : "end",
              }}
            >
              <SecondaryActions
                selectedTask={selectedTask}
                dynamicForkInstances={
                  dfOptions.length > 0 ? (
                    <DropdownButton
                      buttonProps={{
                        color: "secondary",
                        size: "small",
                        style: {
                          fontSize: "9pt",
                        },
                      }}
                      options={dfOptions.map((option: ExecutionTask) => ({
                        label: (
                          <>
                            {dropdownIcon(option.status)}{" "}
                            {option?.workflowTask?.taskReferenceName}
                          </>
                        ),
                        handler: () => handleSelectTask(option),
                      }))}
                    >
                      Instances
                    </DropdownButton>
                  ) : null
                }
                containerQueryState={containerQueryState}
              />
            </Box>
          </Box>
        </Box>
      </Box>

      <Tabs
        value={currentTab}
        style={{ marginBottom: 0 }}
        contextual
        variant="scrollable"
        scrollButtons={containerQueryState["small"] ? true : "auto"}
        allowScrollButtonsMobile
      >
        <Tab label="Summary" onClick={() => changeCurrentTab(SUMMARY_TAB)} />
        <Tab
          label="Input"
          onClick={() => changeCurrentTab(INPUT_TAB)}
          disabled={!selectedTask.status}
        />
        <Tab
          label="Output"
          onClick={() => changeCurrentTab(OUTPUT_TAB)}
          disabled={!selectedTask.status}
        />
        <Tab
          label="Logs"
          onClick={() => changeCurrentTab(LOGS_TAB)}
          disabled={!selectedTask.status}
        />
        <Tab
          label="JSON"
          onClick={() => changeCurrentTab(JSON_TAB)}
          disabled={!selectedTask.status}
        />
        <Tab
          label="Definition"
          onClick={() => changeCurrentTab(DEFINITION_TAB)}
        />
      </Tabs>
      <Paper square elevation={0}>
        {currentTab === SUMMARY_TAB && (
          <Box
            style={{
              overflowY: "auto",
              overflowX: "hidden",
              maxHeight: "calc(100vh - 100px)",
            }}
          >
            <TaskSummary taskResult={selectedTask} />
            {maybeStatusForm}
          </Box>
        )}
        {currentTab === INPUT_TAB && (
          <ReactJson
            src={selectedTask.inputData ?? {}}
            title="Task input"
            overflowY="auto"
            overflowX="hidden"
            workflowName={workflowName}
            editorHeight="calc(100vh - 280px)"
          />
        )}
        {currentTab === OUTPUT_TAB && (
          <ReactJson
            src={
              isReRunFromTaskInProgress ? {} : (selectedTask.outputData ?? {})
            }
            title="Task output"
            overflowY="auto"
            overflowX="hidden"
            workflowName={workflowName}
            editorHeight="calc(100vh - 280px)"
          />
        )}
        {currentTab === LOGS_TAB && (
          <Box
            style={{
              overflowY: "auto",
              overflowX: "hidden",
              maxHeight: "calc(100vh - 200px)",
            }}
          >
            <TaskLogs
              rightPanelActor={rightPanelActor}
              containerQueryState={containerQueryState}
            />
          </Box>
        )}
        {currentTab === JSON_TAB && (
          <ReactJson
            src={selectedTask}
            title="Task Execution JSON"
            overflowY="auto"
            overflowX="hidden"
            workflowName={workflowName}
            editorHeight="calc(100vh - 280px)"
          />
        )}
        {currentTab === DEFINITION_TAB && (
          <ReactJson
            src={selectedTask.workflowTask}
            title="Task definition/Runtime config"
            overflowY="auto"
            overflowX="hidden"
            workflowName={workflowName}
            editorHeight="calc(100vh - 280px)"
          />
        )}
      </Paper>
    </Paper>
  );
};

function dropdownIcon(status: string) {
  let icon;
  switch (status) {
    case TaskStatus.COMPLETED:
      icon = "\u2705";
      break; // Green-checkmark
    case TaskStatus.COMPLETED_WITH_ERRORS:
      icon = "\u2757";
      break; // Exclamation
    case TaskStatus.CANCELED:
      icon = "\uD83D\uDED1";
      break; // stopsign
    case TaskStatus.IN_PROGRESS:
    case TaskStatus.SCHEDULED:
      icon = "\u231B";
      break; // hourglass
    case TaskStatus.TIMED_OUT:
      icon = "\u26D4";
      break;
    case TaskStatus.FAILED:
      icon = "\u2757";
      break;
    default:
      icon = "\u274C"; // red-X
  }

  return icon + "\u2003";
}
