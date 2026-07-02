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
import { IterationSection } from "./IterationSection";
import ClipboardCopy from "components/ui/ClipboardCopy";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import StatusBadge from "components/StatusBadge";
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
import { ActorRef } from "xstate";
import { UpdateTaskStatusForm } from "..";
import {
  DEFINITION_TAB,
  PLUGIN_PANEL_TAB_BASE,
  INPUT_TAB,
  JSON_TAB,
  LOGS_TAB,
  OUTPUT_TAB,
  SUMMARY_TAB,
} from "../state/constants";
import TaskLogs from "../TaskLogs";
import TaskSummary from "../TaskSummary";
import { pluginRegistry } from "plugins/registry";
import { RightPanelContextEventTypes, RightPanelEvents } from "./state";
import { useRightPanelActor } from "./state/hook";
import { SummaryTask } from "./SummaryTask";
import { dropdownIcon } from "./dropdownIcon";
import { SecondaryActions } from "./SecondaryActions";

const executionTaskHeaderContainerQuery = {
  small: { maxWidth: 699 },
  large: { minWidth: 700 },
};

const rerunFromForkAndDowhileTasksEnabled = featureFlags.isEnabled(
  FEATURES.ENABLE_RERUN_FROM_FORK_AND_DOWHILE_TASKS,
);

export interface RightPanelProps {
  rightPanelActor: ActorRef<RightPanelEvents>;
  workflowName: string;
  workflowStatus: string;
  doWhileSelection?: DoWhileSelection[];
}

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
      parentDoWhileRef,
      errorMessage,
      currentTab,
      maybeSiblings,
      isReRunFromTaskInProgress,
      executionId,
      authHeaders,
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

  // Plugin execution-panel tabs for this task type, minus any whose shouldShow
  // predicate excludes this specific task. Computed once so the tab list and the
  // tab content below stay index-aligned.
  const pluginPanels = pluginRegistry
    .getTaskExecutionPanels(`${selectedTask?.taskType}`)
    .filter((panel) => !panel.shouldShow || panel.shouldShow(selectedTask));

  // If the summary task is selected just show a small summary
  if (selectedTask?.taskType === "TASK_SUMMARY")
    return <SummaryTask selectedTask={selectedTask} onClose={onClosePanel!} />;

  const isKeptLastNPruned = (selectedTask as any)?._summarized === true;

  const prunedNotice = (
    <Box
      sx={{
        px: 2,
        py: 3,
        color: "text.secondary",
        fontSize: 13,
      }}
    >
      This data isn&apos;t available in summarize mode.
    </Box>
  );

  return !selectedTask ? null : (
    <Paper square elevation={0} id="execution-page-right-panel">
      {errorMessage && (
        <SnackbarMessage
          message={errorMessage}
          severity="error"
          onDismiss={clearErrorMessage}
        />
      )}
      <Box sx={{ display: "flex" }}>
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
            sx={{ opacity: 0.5 }}
          >
            <CloseIcon />
          </IconButton>
        </Box>
        <Box sx={{ width: "100%" }}>
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
            <Box sx={{ flexGrow: 1, minWidth: 0 }}>
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
              {selectedTask?.status === "PENDING" ||
              isKeptLastNPruned ? null : (
                <Box sx={{ fontSize: 14, width: "100%" }}>
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
              {((retryIterationOptions != null &&
                retryIterationOptions.length > 1) ||
                selectedTask?.taskType === TaskType.DO_WHILE) && (
                <Box sx={{ width: "100%", mt: 1 }}>
                  <IterationSection
                    selectedTask={selectedTask}
                    retryIterationOptions={retryIterationOptions ?? []}
                    isIteration={isIteration ?? false}
                    handleSelectTask={handleSelectTask}
                    handleSelectDoWhileIteration={handleSelectDoWhileIteration}
                    doWhileSelection={doWhileSelection}
                    executionId={executionId}
                    authHeaders={authHeaders}
                    parentDoWhileRef={parentDoWhileRef}
                  />
                </Box>
              )}
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
                        style: { fontSize: "9pt" },
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

      <>
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
          {pluginPanels.map((panel, i) => (
            <Tab
              key={panel.id}
              label={panel.label}
              value={PLUGIN_PANEL_TAB_BASE + i}
              onClick={() => changeCurrentTab(PLUGIN_PANEL_TAB_BASE + i)}
              disabled={!selectedTask.status}
            />
          ))}
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
          {currentTab === INPUT_TAB &&
            (!selectedTask.inputData ? (
              prunedNotice
            ) : (
              <ReactJson
                src={selectedTask.inputData}
                title="Task input"
                overflowY="auto"
                overflowX="hidden"
                workflowName={workflowName}
                editorHeight="calc(100vh - 280px)"
              />
            ))}
          {currentTab === OUTPUT_TAB &&
            (!selectedTask.outputData ? (
              prunedNotice
            ) : (
              <ReactJson
                src={isReRunFromTaskInProgress ? {} : selectedTask.outputData}
                title="Task output"
                overflowY="auto"
                overflowX="hidden"
                workflowName={workflowName}
                editorHeight="calc(100vh - 280px)"
              />
            ))}
          {currentTab === LOGS_TAB &&
            (isKeptLastNPruned ? (
              prunedNotice
            ) : (
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
            ))}
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
          {pluginPanels.map((panel, i) =>
            currentTab === PLUGIN_PANEL_TAB_BASE + i ? (
              <Box
                key={panel.id}
                style={{
                  overflowY: "auto",
                  overflowX: "hidden",
                  maxHeight: "calc(100vh - 100px)",
                }}
              >
                <panel.component taskResult={selectedTask} />
              </Box>
            ) : null,
          )}
        </Paper>
      </>
    </Paper>
  );
};
