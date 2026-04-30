import LaunchIcon from "@mui/icons-material/Launch";
import { Box, Stack, Tooltip } from "@mui/material";
import { AutoRefreshButton, Button, Heading, LinearProgress } from "components";
import MuiAlert from "components/ui/MuiAlert";
import MuiTypography from "components/ui/MuiTypography";
import NavLink from "components/ui/NavLink";
import { SnackbarMessage } from "components/ui/SnackbarMessage";
import StatusBadge from "components/StatusBadge";
import TwoPanesDivider from "components/ui/TwoPanesDivider";
import { Flow } from "components/features/flow/Flow";
import { CopyClipboardButton } from "components/ui/inputs/CopyClipboardButton";
import OpenIcon from "components/icons/OpenIcon";
import ButtonLinks from "components/layout/header/ButtonLinks";
import { ConductorSectionHeader } from "components/layout/section/ConductorSectionHeader";
import { SidebarContext } from "components/providers/sidebar/context/SidebarContext";
import { path as _path } from "lodash/fp";
import { useContext, useMemo } from "react";
import { Helmet } from "react-helmet";
import { useLocation } from "react-router";
import { colors } from "theme/tokens/variables";
import { WorkflowExecutionStatus } from "types/Execution";
import { TaskStatus } from "types/TaskStatus";
import { openInNewTab } from "utils/helpers";
import { usePushHistory } from "utils/hooks/usePushHistory";
import ActionModule from "./ActionModule";
import InputOutput from "./ExecutionInputOutput";
import ExecutionJson from "./ExecutionJson";
import ExecutionSummary from "./ExecutionSummary";
import LeftPanelTabs from "./LeftPanelTabs";
import { RightPanel } from "./RightPanel";
import { TaskList } from "./TaskList/TaskList";
import Timeline from "./Timeline";
import { FlowExecutionContextProvider } from "./state";
import { useExecutionMachine } from "./state/hook";
import { ExecutionTabs } from "./state/types";
import { WorkflowIntrospection } from "pages/execution/WorkflowIntrospection";
import Agent from "components/features/agent/Agent";
import { AgentDisplayMode } from "components/features/agent/agent-types";
import {
  agentDisplayModeAtom,
  agentFirstUseAtom,
} from "components/features/agent/agentAtomsStore";
import { useAtom } from "jotai";

const SecondaryActions = ({
  execution,
  countdownActor,
  onRestartExecutionWithLatestDefinitions,
  onRestartExecutionWithCurrentDefinitions,
  onRetryExecutionFromFailed,
  onResumeExecution,
  onTerminateExecution,
  onPauseExecution,
  onRetryResumeSubworkflow,
  rerunExecutionWithLatestDefinitions,
  createSheduleWithLatestDefinitions,
  refetch,
}) => {
  const isDynamic = _path("input._systemMetadata.dynamic", execution);
  return (
    execution && (
      <Box
        sx={{
          display: "flex",
          gap: 2,
          alignItems: "center",
        }}
      >
        {execution.parentWorkflowId && (
          <Button
            variant="text"
            size="small"
            onClick={() =>
              openInNewTab(`/execution/${execution.parentWorkflowId}`)
            }
            endIcon={<LaunchIcon />}
            sx={{ minWidth: "fit-content" }}
          >
            Parent Workflow
          </Button>
        )}

        <Box
          sx={{
            display: "flex",
            gap: 2,
            flexGrow: 0,
            flexShrink: 0,
          }}
        >
          {isDynamic ? null : (
            <Tooltip title="(CMD+Click) to open in a new tab" placement="top">
              <Button
                variant="text"
                size="small"
                startIcon={<OpenIcon />}
                sx={{ minWidth: "fit-content" }}
                component={NavLink}
                to={`/workflowDef/${encodeURIComponent(
                  execution.workflowType || execution.workflowName,
                )}/${execution.workflowVersion}`}
              >
                View definition
              </Button>
            </Tooltip>
          )}
          <AutoRefreshButton
            buttonProps={{
              color: "secondary",
              size: "small",
              style: {
                fontSize: "9pt",
              },
            }}
            countdownActor={countdownActor}
            execution={execution}
            refetch={refetch}
          />
          <ActionModule
            execution={execution}
            rerunExecutionWithLatestDefinitions={
              rerunExecutionWithLatestDefinitions
            }
            createSheduleWithLatestDefinitions={
              createSheduleWithLatestDefinitions
            }
            onRestartExecutionWithLatestDefinitions={
              onRestartExecutionWithLatestDefinitions
            }
            onRestartExecutionWithCurrentDefinitions={
              onRestartExecutionWithCurrentDefinitions
            }
            onRetryExecutionFromFailed={onRetryExecutionFromFailed}
            onResumeExecution={onResumeExecution}
            onTerminateExecution={onTerminateExecution}
            onPauseExecution={onPauseExecution}
            onRetryResumeSubworkflow={onRetryResumeSubworkflow}
          />
        </Box>
      </Box>
    )
  );
};

const FailureAlert = ({ failedWFLink, alertText }) => {
  const navigate = usePushHistory();

  const alertStyle = {
    padding: "0 10px",
    fontSize: "12px",
    height: "28px",
    width: "fit-content",
    fontWeight: "500",
    cursor: "pointer",
    marginRight: "10px",
    ".MuiAlert-message, .css-1ytlwq5-MuiAlert-icon": {
      padding: "4px 0px",
    },
    ".css-1ytlwq5-MuiAlert-icon": { marginRight: "8px" },
    "&:hover": {
      border: "1px solid #badfff",
    },
    alignItems: "center",
  };

  return (
    <MuiAlert
      style={alertStyle}
      variant="outlined"
      color="info"
      onClick={() => navigate(`/execution/${failedWFLink}`)}
    >
      {alertText}
    </MuiAlert>
  );
};

const ReasonForIncompletion = ({ reason, navigate, location }) => {
  if (!reason) return null;

  if (reason.length >= 300) {
    return (
      <Box>
        {reason.substr(0, 60)}... [
        <MuiTypography
          component="span"
          color="#1976d2"
          fontWeight="bold"
          cursor="pointer"
          onClick={() => {
            navigate(`${location.pathname}?tab=summary`);
          }}
        >
          View full message
        </MuiTypography>
        ]
      </Box>
    );
  }

  return <>{reason}</>;
};

const ExecutionAlert = ({
  execution,
  openedTab,
  failedTaskWithReason,
  handleJumpToTask,
}) => {
  const navigate = usePushHistory();
  const location = useLocation();

  if (
    execution?.rateLimited ||
    (execution?.reasonForIncompletion &&
      execution?.status !== WorkflowExecutionStatus.COMPLETED)
  ) {
    return (
      <MuiAlert
        severity={execution?.rateLimited ? "warning" : "error"}
        style={{
          ".MuiAlert-message": {
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            width: "100%",
            gap: 5,
          },
        }}
      >
        <Box>
          {execution?.rateLimited ? (
            "This execution is rate limited and will be executed once previous executions are completed."
          ) : (
            <ReasonForIncompletion
              reason={execution?.reasonForIncompletion}
              navigate={navigate}
              location={location}
            />
          )}
        </Box>

        {openedTab === ExecutionTabs.DIAGRAM_TAB && failedTaskWithReason && (
          <MuiTypography
            color="error"
            whiteSpace="nowrap"
            cursor="pointer"
            fontWeight={400}
            onClick={handleJumpToTask}
          >
            Jump to task
          </MuiTypography>
        )}
      </MuiAlert>
    );
  }
  return null;
};

export default function Execution() {
  const [
    {
      selectTask,
      expandDynamic,
      collapseDynamic,
      clearError,
      rerunExecutionWithLatestDefinitions,
      createSheduleWithLatestDefinitions,
      restartExecutionWithLatestDefinitions,
      restartExecutionWithCurrentDefinitions,
      retryExcutionFromFailed,
      retryResumeSubworkflow,
      resumeExecution,
      terminateExecution,
      pauseExecution,
      changeExecutionTab,
      closeRightPanel,
      refetch,
      handleUpdateVariables,
      selectNode,
    },
    {
      flowActor,
      execution,
      executionId,
      isReady,
      selectedTask,
      executionStatusMap,
      countdownActor,
      maybeError,
      maybeMessage,
      openedTab,
      taskListActor,
      rightPanelActor,
      isNoAccess,
      doWhileSelection,
      nodes,
    },
  ] = useExecutionMachine();
  const location = useLocation();

  const { open: isSideBarOpen } = useContext(SidebarContext);

  const [, setAgentFirstUse] = useAtom(agentFirstUseAtom);
  const [agentDisplayMode, setAgentDisplayMode] = useAtom(agentDisplayModeAtom);

  // The assistant panel is open whenever the agent is in expanded mode.
  // Deriving from the atom (rather than XState) means the sidebar button and
  // cross-page navigation drive this panel without any extra sync effect.
  const isAssistantPanelOpen =
    agentDisplayMode === AgentDisplayMode.FLOATING_EXPANDED;

  const isFailure = (workflow) => {
    const workflowInput = workflow?.input;
    if (
      workflowInput?.reason &&
      workflowInput?.failureTaskId &&
      workflowInput?.workflowId &&
      workflowInput?.failureStatus
    ) {
      return workflow.input.workflowId;
    }
  };

  const isExecutionView = location.pathname.startsWith("/execution/");

  const failureWorkflowId = isFailure(execution);

  const leftPanelContent = (
    <>
      {execution && (
        <Box
          sx={{
            display: "flex",
            flexDirection: "column",
            overflow: "hidden",
            width: "100%",
            height: "100%",
            flexGrow: 1,
            position: "relative",
          }}
        >
          <>
            {openedTab === ExecutionTabs.DIAGRAM_TAB &&
              execution &&
              flowActor && (
                <FlowExecutionContextProvider
                  onExpandDynamic={expandDynamic}
                  onCollapseDynamic={collapseDynamic}
                >
                  <Flow
                    flowActor={flowActor}
                    readOnly={true}
                    leftPanelExpanded={rightPanelActor}
                    isExecutionView={isExecutionView}
                  />
                </FlowExecutionContextProvider>
              )}
            {openedTab === ExecutionTabs.TASK_LIST_TAB && taskListActor && (
              <TaskList
                tasks={execution.tasks}
                taskListActor={taskListActor}
                executionAlert={execution.reasonForIncompletion}
              />
            )}
            {openedTab === ExecutionTabs.TIMELINE_TAB && (
              <Timeline
                selectedTask={selectedTask}
                tasks={execution.tasks}
                executionStatusMap={executionStatusMap}
                onClick={selectTask}
              />
            )}
            {openedTab === ExecutionTabs.WORKFLOW_INTROSPECTION && (
              <WorkflowIntrospection
                workflow={execution}
                selectTask={selectTask}
              />
            )}
            {openedTab === ExecutionTabs.SUMMARY_TAB && (
              <ExecutionSummary execution={execution} />
            )}
            {openedTab === ExecutionTabs.WORKFLOW_INPUT_OUTPUT_TAB && (
              <InputOutput
                execution={execution}
                data={[
                  {
                    title: "Input",
                    src: execution.input,
                    hidden: false,
                    style: {
                      minWidth: 400,
                    },
                  },
                  {
                    title: "Output",
                    src: execution.output,
                    hidden: false,
                    style: {
                      minWidth: 400,
                    },
                  },
                ]}
              />
            )}
            {openedTab === ExecutionTabs.JSON_TAB && (
              <ExecutionJson execution={execution} />
            )}
            {openedTab === ExecutionTabs.VARIABLES_TAB && (
              <InputOutput
                isEditable={
                  execution?.status === WorkflowExecutionStatus.RUNNING
                    ? true
                    : false
                }
                handleUpdate={handleUpdateVariables}
                execution={execution}
                data={[
                  {
                    title: "Variables",
                    src: execution.variables,
                    hidden: false,
                    style: {
                      minWidth: 340,
                    },
                  },
                ]}
              />
            )}
            {openedTab === ExecutionTabs.TASKS_TO_DOMAIN_TAB && (
              <InputOutput
                execution={execution}
                data={[
                  {
                    title: "Task to Domain",
                    src: execution.taskToDomain,
                    hidden: !execution.taskToDomain,
                    style: {
                      minWidth: 340,
                    },
                  },
                ]}
              />
            )}
          </>
        </Box>
      )}
    </>
  );

  const rightPanelContent = (
    <>
      {isAssistantPanelOpen ? (
        <Box
          sx={{
            height: "100%",
            width: "100%",
            display: "flex",
            flex: "1 1 auto",
            overflowY: "scroll",
            flexDirection: "column",
            marginTop: 0,
            color: (theme) =>
              theme.palette?.mode === "dark" ? colors.gray14 : undefined,
            backgroundColor: (theme) =>
              theme.palette?.mode === "dark" ? colors.gray01 : undefined,
          }}
        >
          <Agent mode={AgentDisplayMode.TABBED} />
        </Box>
      ) : (
        rightPanelActor && (
          <Box
            sx={{
              height: "100%",
              width: "100%",
              display: "flex",
              flex: "1 1 auto",
              overflowY: "scroll",
              flexDirection: "column",
              marginTop: 0,
              color: (theme) =>
                theme.palette?.mode === "dark" ? colors.gray14 : undefined,
              backgroundColor: (theme) =>
                theme.palette?.mode === "dark" ? colors.gray01 : undefined,
            }}
          >
            <RightPanel
              rightPanelActor={rightPanelActor}
              workflowName={execution?.workflowName}
              workflowStatus={execution?.status}
              doWhileSelection={doWhileSelection}
            />
          </Box>
        )
      )}
    </>
  );

  const workflowTitle = useMemo(
    () => (execution?.workflowType || execution?.workflowName) ?? null,
    [execution?.workflowType, execution?.workflowName],
  );

  const failedTaskWithReason = useMemo(
    () =>
      execution?.tasks?.find(
        (task) =>
          task?.status === TaskStatus.FAILED && task?.reasonForIncompletion,
      ),
    [execution?.tasks],
  );

  const handleJumpToTask = () => {
    const maybeSelectedNode = nodes?.find(
      (node) => node?.id === failedTaskWithReason?.referenceTaskName,
    );
    if (maybeSelectedNode) {
      selectNode(maybeSelectedNode);
    }
  };

  return (
    <Box
      sx={{
        height: "100%",
        width: "100%",
      }}
    >
      <Helmet>
        <title>
          Execution - {workflowTitle === null ? "" : workflowTitle} -{" "}
          {executionId}
        </title>
      </Helmet>
      <SnackbarMessage
        message={maybeError?.text}
        severity={maybeError?.severity}
        onDismiss={clearError}
      />
      <SnackbarMessage
        message={maybeMessage?.text}
        severity={maybeMessage?.severity}
        onDismiss={clearError}
      />
      <Box
        id="workflow-execution-detail-container"
        sx={{
          height: "100%",
          flex: "1 1 0%",
          position: "relative",
          display: "flex",
          flexDirection: "column",
        }}
      >
        {!isReady && !isNoAccess && <LinearProgress />}

        {execution && (
          <Box>
            <ConductorSectionHeader
              id="workflow-execution-header-section"
              sx={{ minHeight: "65px" }}
              title={
                <Stack
                  flexDirection="row"
                  alignItems="center"
                  flexGrow={1}
                  minWidth={0}
                  gap={1}
                >
                  <Tooltip title={workflowTitle} arrow>
                    <Box
                      sx={{
                        overflow: "hidden",
                      }}
                    >
                      <Heading
                        fontWeight={500}
                        fontSize={"14pt"}
                        level={1}
                        sx={{
                          textOverflow: "ellipsis",
                          overflow: "hidden",
                          whiteSpace: "nowrap",
                          textTransform: "none",
                          letterSpacing: "normal",
                        }}
                      >
                        {workflowTitle}
                      </Heading>
                    </Box>
                  </Tooltip>
                  <CopyClipboardButton text={workflowTitle} />
                  <StatusBadge status={execution?.status} />
                </Stack>
              }
              breadcrumbItems={[
                { label: "Workflow Executions", to: "/executions" },
                {
                  label: execution.workflowId || "",
                  to: "",
                  icon: <CopyClipboardButton text={execution.workflowId} />,
                },
              ]}
              buttonsComponent={
                <Stack
                  flexDirection="row"
                  alignItems="center"
                  justifyContent={["start", "end", "end"]}
                  gap={2}
                  rowGap={2}
                  flexWrap="wrap"
                >
                  {failureWorkflowId && (
                    <FailureAlert
                      failedWFLink={failureWorkflowId}
                      alertText="Triggered by workflow failure"
                    />
                  )}

                  {execution?.output?.["conductor.failure_workflow"] && (
                    <FailureAlert
                      failedWFLink={
                        execution.output["conductor.failure_workflow"]
                      }
                      alertText="Triggered failure workflow"
                    />
                  )}

                  <ButtonLinks
                    isSideBarOpen={isSideBarOpen}
                    sx={{ gridArea: "links" }}
                    showDropdownOnly={true}
                  />

                  <SecondaryActions
                    execution={execution}
                    countdownActor={countdownActor}
                    rerunExecutionWithLatestDefinitions={
                      rerunExecutionWithLatestDefinitions
                    }
                    createSheduleWithLatestDefinitions={
                      createSheduleWithLatestDefinitions
                    }
                    onRestartExecutionWithLatestDefinitions={
                      restartExecutionWithLatestDefinitions
                    }
                    onRestartExecutionWithCurrentDefinitions={
                      restartExecutionWithCurrentDefinitions
                    }
                    onRetryExecutionFromFailed={retryExcutionFromFailed}
                    onResumeExecution={resumeExecution}
                    onTerminateExecution={terminateExecution}
                    onPauseExecution={pauseExecution}
                    onRetryResumeSubworkflow={retryResumeSubworkflow}
                    refetch={refetch}
                  />
                </Stack>
              }
            />

            <Box
              sx={{
                backgroundColor: "white",
                borderBottom: "1px solid rgba(0,0,0,.25)",
              }}
            >
              <ExecutionAlert
                execution={execution}
                openedTab={openedTab}
                failedTaskWithReason={failedTaskWithReason}
                handleJumpToTask={handleJumpToTask}
              />
              <LeftPanelTabs
                execution={execution}
                openedTab={openedTab}
                onChangeExecutionTab={changeExecutionTab}
                onToggleAssistant={() => {
                  setAgentFirstUse(true);
                  setAgentDisplayMode(
                    isAssistantPanelOpen
                      ? AgentDisplayMode.CLOSED
                      : AgentDisplayMode.FLOATING_EXPANDED,
                  );
                }}
              />
            </Box>
          </Box>
        )}

        <Box
          sx={{
            width: "100%",
            overflow: "visible",
            display: "flex",
            flexDirection: "column",
            backgroundColor: "transparent",
            fontSize: "13px",
            position: "relative",
            zIndex: 1,
            flexGrow: 1,
          }}
        >
          <Box
            sx={{
              display: "flex",
              height: "100%",
              position: "absolute",
              overflow: "visible",
              userSelect: "text",
              flexDirection: "row",
              left: "0px",
              right: "0px",
            }}
          >
            <TwoPanesDivider
              leftPanelContent={leftPanelContent}
              rightPanelContent={rightPanelContent}
              leftPanelExpanded={
                !isAssistantPanelOpen && rightPanelActor === undefined
              }
              setLeftPanelExpanded={closeRightPanel}
              hideCollapseButton={true}
            />
          </Box>
        </Box>
      </Box>
    </Box>
  );
}
