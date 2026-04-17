import { Box, Link } from "@mui/material";
import { WarningIcon } from "@phosphor-icons/react";
import { useActor, useSelector } from "@xstate/react";
import ConductorTooltip from "components/ui/ConductorTooltip";
import theme from "components/features/flow/theme";
import DocsIcon from "components/icons/DocsIcon";
import {
  BusinessRuleForm,
  DoWhileForm,
  DynamicForkOperatorForm,
  DynamicOperatorForm,
  EventTaskForm,
  GetSignedJwtForm,
  GetWorkflowTaskForm,
  HTTPPollTaskForm,
  HTTPTaskForm,
  INLINETaskForm,
  JDBCTaskForm,
  JOINTaskForm,
  JSONJQTransformForm,
  KafkaTaskForm,
  OpsGenieTaskForm,
  QueryProcessorTaskForm,
  SetVariableOperatorForm,
  SimpleTaskForm,
  StartWorkflowTaskForm,
  SubWorkflowOperatorForm,
  SwitchOperatorForm,
  TerminateOperatorForm,
  TerminateWorkflowForm,
  UnknownTaskForm,
  WaitTaskForm,
  YieldTaskForm,
} from "pages/definition/EditorPanel/TaskFormTab/forms";
import TaskFormHeader from "pages/definition/EditorPanel/TaskFormTab/forms/TaskFormHeader/TaskFormHeader";
import { FormMachineActionTypes } from "pages/definition/EditorPanel/TaskFormTab/state";
import { WorkflowEditContext } from "pages/definition/state";
import { pluginRegistry } from "plugins/registry";
import {
  FunctionComponent,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { useAuth } from "components/features/auth";
import { colors } from "theme/tokens/variables";
import { FormTaskType, TaskDef, TaskType } from "types";
import { updateField } from "utils/fieldHelpers";
import { FEATURES, featureFlags } from "utils/flags";
import { TaskStats } from "./TaskStats/TaskStats";
import BoundaryTimerSection from "./forms/BoundaryTimerSection";
import { ConductorCacheOutput } from "./forms/ConductorCacheOutputForm";
import { MCPTaskForm } from "./forms/MCPTaskForm";
import { MaybeVariable } from "./forms/MaybeVariable";
import { Optional } from "./forms/OptionalFieldForm";
import TaskFormSection from "./forms/TaskFormSection";
import { OpenTestTaskButton } from "./forms/TestTaskButton/OpenTestTaskButton";
import { UpdateTaskForm } from "./forms/UpdateTaskForm";
import { TaskFormProps } from "./forms/types";
import { TaskFormContext } from "./state";
import { taskDescriptions } from "./taskDescription";

const ENABLE_TASK_STATS = featureFlags.isEnabled(FEATURES.DISABLE_TASK_STATS);

/**
 * Get the task form component for a given task type.
 * First checks the plugin registry for enterprise task forms,
 * then falls back to core OSS task forms.
 */
const getTaskForm = (type: string) => {
  // First check plugin registry for enterprise task forms
  const pluginForm = pluginRegistry.getTaskForm(type);
  if (pluginForm) {
    return pluginForm as FunctionComponent<TaskFormProps>;
  }

  // Core OSS task forms
  switch (type) {
    // System Tasks
    case TaskType.EVENT:
      return EventTaskForm;
    case TaskType.HTTP:
      return HTTPTaskForm as FunctionComponent<TaskFormProps>;
    case TaskType.HTTP_POLL:
      return HTTPPollTaskForm;
    case TaskType.JSON_JQ_TRANSFORM:
      return JSONJQTransformForm;
    case TaskType.INLINE:
      return INLINETaskForm;
    case TaskType.KAFKA_PUBLISH:
      return KafkaTaskForm;
    case TaskType.BUSINESS_RULE:
      return BusinessRuleForm;
    case TaskType.QUERY_PROCESSOR:
      return QueryProcessorTaskForm;
    case TaskType.GET_SIGNED_JWT:
      return GetSignedJwtForm;
    case TaskType.UPDATE_TASK:
      return UpdateTaskForm;
    case TaskType.MCP:
      return MCPTaskForm;

    // Operators
    case TaskType.DECISION:
    case TaskType.SWITCH:
      return SwitchOperatorForm;
    case TaskType.DO_WHILE:
      return DoWhileForm;
    case TaskType.FORK_JOIN_DYNAMIC:
      return DynamicForkOperatorForm;
    case TaskType.DYNAMIC:
      return DynamicOperatorForm;
    case TaskType.TERMINATE:
      return TerminateOperatorForm;
    case TaskType.SET_VARIABLE:
      return SetVariableOperatorForm;
    case TaskType.SUB_WORKFLOW:
      return SubWorkflowOperatorForm;
    case TaskType.JOIN:
      return JOINTaskForm;
    case TaskType.WAIT:
      return WaitTaskForm as FunctionComponent<TaskFormProps>;
    case TaskType.TERMINATE_WORKFLOW:
      return TerminateWorkflowForm;
    case TaskType.START_WORKFLOW:
      return StartWorkflowTaskForm;
    case TaskType.GET_WORKFLOW:
      return GetWorkflowTaskForm;
    case TaskType.YIELD:
      return YieldTaskForm;

    // Alerting
    case TaskType.OPS_GENIE:
      return OpsGenieTaskForm;

    // Workers
    case TaskType.SIMPLE:
      return SimpleTaskForm;
    case TaskType.JDBC:
      return JDBCTaskForm;

    // Unknown task type - show generic JSON form
    default:
      return UnknownTaskForm;
  }
};

const TaskForType: FunctionComponent = () => {
  const { formTaskActor } = useContext(TaskFormContext);
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const [, send] = useActor(formTaskActor!);
  const taskFormHeaderActor = useSelector(
    formTaskActor!,
    (state) => state.context.taskHeaderActor,
  );

  const taskType = useSelector(
    formTaskActor!,
    (state) => state.context.taskChanges.type,
  );

  const task = useSelector(
    formTaskActor!,
    (state) => state.context.taskChanges,
  );

  const collapseWorkflowList = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.collapseWorkflowList,
  );

  const handleTaskChange = (taskChanges: Partial<TaskDef>) => {
    send({ type: FormMachineActionTypes.UPDATE_TASK, taskChanges });
  };
  const handleToggleExpand = (workflowName: string) => {
    send({
      type: FormMachineActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST,
      workflowName,
    });
    handleTaskChange({});
  };

  const TaskForTypeC = getTaskForm(taskType);

  return (
    <TaskForTypeC
      task={task}
      onChange={(task: TaskDef) => {
        handleTaskChange(task);
      }}
      onToggleExpand={(workflowName: string) => {
        handleToggleExpand(workflowName);
      }}
      collapseWorkflowList={collapseWorkflowList}
      taskFormHeaderActor={taskFormHeaderActor}
    />
  );
};

/**
 * Core OSS task documentation URLs.
 * Enterprise task doc URLs are registered via the plugin system.
 */
const coreTaskDocUrls: { [key: string]: string } = {
  [TaskType.HTTP]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/http-task",
  [TaskType.INLINE]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/inline-task",
  [TaskType.JOIN]: "https://orkes.io/content/docs/reference-docs/join-task",
  [TaskType.DO_WHILE]:
    "https://orkes.io/content/docs/reference-docs/do-while-task",
  [TaskType.FORK_JOIN]:
    "https://orkes.io/content/docs/reference-docs/fork-task",
  [TaskType.FORK_JOIN_DYNAMIC]:
    "https://orkes.io/content/docs/reference-docs/dynamic-fork-task",
  [TaskType.DYNAMIC]:
    "https://orkes.io/content/docs/reference-docs/dynamic-task",
  [TaskType.DECISION]:
    "https://orkes.io/content/docs/reference-docs/decision-task",
  [TaskType.KAFKA_PUBLISH]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/kafka-publish-task",
  [TaskType.JSON_JQ_TRANSFORM]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/json-jq-transform-task",
  [TaskType.SWITCH]: "https://orkes.io/content/docs/reference-docs/switch-task",
  [TaskType.TERMINATE]:
    "https://orkes.io/content/docs/reference-docs/terminate-task",
  [TaskType.SET_VARIABLE]:
    "https://orkes.io/content/docs/reference-docs/set-variable-task",
  [TaskType.TERMINATE_WORKFLOW]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/terminate-workflow",
  [TaskType.EVENT]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/event-task",
  [TaskType.SUB_WORKFLOW]:
    "https://orkes.io/content/docs/reference-docs/sub-workflow-task",
  [TaskType.WAIT]: "https://orkes.io/content/docs/reference-docs/wait-task",
  [TaskType.BUSINESS_RULE]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/business-rule",
  [TaskType.START_WORKFLOW]:
    "https://orkes.io/content/docs/reference-docs/start-workflow",
  [TaskType.HTTP_POLL]:
    "https://orkes.io/content/docs/reference-docs/system-tasks/http-poll-task",
  [TaskType.SIMPLE]: "https://orkes.io/content/reference-docs/worker-task",
  [TaskType.JDBC]: "https://orkes.io/content/reference-docs/system-tasks/jdbc",
  [TaskType.QUERY_PROCESSOR]:
    "https://orkes.io/content/reference-docs/system-tasks/query-processor",
  [TaskType.OPS_GENIE]:
    "https://orkes.io/content/reference-docs/system-tasks/opsgenie",
  [TaskType.UPDATE_TASK]:
    "https://orkes.io/content/reference-docs/system-tasks/update-task",
  [TaskType.GET_WORKFLOW]:
    "https://orkes.io/content/reference-docs/operators/get-workflow",
  [TaskType.GET_SIGNED_JWT]:
    "https://orkes.io/content/reference-docs/system-tasks/get-signed-jwt",
  [TaskType.YIELD]: "https://orkes.io/content/reference-docs/operators/yield",
};

/**
 * Get the documentation URL for a task type.
 * First checks plugin-registered URLs, then falls back to core URLs.
 */
const getTaskDocUrl = (taskType: string): string | undefined => {
  // First check plugin registry for enterprise doc URLs
  const pluginUrl = pluginRegistry.getTaskDocUrl(taskType);
  if (pluginUrl) {
    return pluginUrl;
  }
  // Fall back to core URLs
  return coreTaskDocUrls[taskType];
};

const TaskFormContent: FunctionComponent = () => {
  const { formTaskActor } = useContext(TaskFormContext);
  const { isTrialExpired } = useAuth();
  const { workflowDefinitionActor } = useContext(WorkflowEditContext);
  const panelRef = useRef<HTMLDivElement>(null);
  const [panelWidth, setPanelWidth] = useState(0);

  const taskType = useSelector(
    formTaskActor!,
    (state) => state.context.originalTask.type,
  );
  const isUnknownTaskType = !Object.values(TaskType).includes(taskType);

  const taskFormHeaderActor = useSelector(
    formTaskActor!,
    (state) => state.context.taskHeaderActor,
  );

  const task = useSelector(
    formTaskActor!,
    (state) => state.context.taskChanges,
  );

  const onChangeRequest = (value: any) =>
    formTaskActor!.send({
      type: FormMachineActionTypes.UPDATE_TASK,
      taskChanges: updateField("inputParameters", value, task),
    });

  const taskDescription =
    taskType && taskDescriptions[taskType as FormTaskType];

  const tasksList = useSelector(
    workflowDefinitionActor!,
    (state) => state.context.workflowChanges?.tasks ?? [],
  );

  const truncate = useCallback(
    (input: string) => {
      let resultText = input;
      if (panelWidth < 653 && panelWidth > 500) {
        resultText = input.length > 10 ? `${input.substring(0, 10)}...` : input;
      } else if (panelWidth < 500) {
        resultText = input.length > 5 ? `${input.substring(0, 5)}...` : input;
      }
      return resultText;
    },
    [panelWidth],
  );
  useEffect(() => {
    if (panelRef.current) {
      const observer = new ResizeObserver((entries) => {
        for (const entry of entries) {
          setPanelWidth(entry.contentRect.width);
        }
      });

      observer.observe(panelRef.current);

      // Cleanup function
      return () => {
        observer.disconnect();
      };
    }
  }, []);
  const handleTaskFieldUpdate = (fieldName: string) => (value: any) => {
    formTaskActor!.send({
      type: FormMachineActionTypes.UPDATE_TASK,
      taskChanges: { ...task, [fieldName]: value?.[fieldName] },
    });
  };

  const taskTypeLabel = taskType === TaskType.MCP ? "CONNECTED APP" : taskType;

  return (
    <Box
      ref={panelRef}
      sx={{
        maxHeight: "100%",
        overflow: "hidden scroll",
        "&::-webkit-scrollbar-track": { backgroundColor: "rgba(0,0,0,.2)" },
      }}
    >
      <Box
        sx={{
          // maxWidth: "800px",
          overflow: "hidden",
        }}
      >
        <Box>
          <Box
            sx={{
              display: "flex",
              padding: "12px 24px",
              alignItems: "center",
              flexWrap: "wrap",
            }}
          >
            <Box
              style={{
                padding: "4px 8px",
                borderRadius: "4px",
                fontSize: "8pt",
                width: "auto",
                background: theme.taskCard.cardLabel.background,
                marginRight: "8px",
                color: "black",
              }}
            >
              {taskTypeLabel}
            </Box>
            {taskType === TaskType.DECISION && (
              <Box
                sx={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  px: 1,
                  py: 0.5,
                  fontSize: "12px",
                  fontWeight: 500,
                  color: "#9A3412",
                  bgcolor: "#FFEDD5",
                  borderRadius: "4px",
                  marginRight: "8px",
                }}
              >
                <WarningIcon size={12} />
                Deprecated
              </Box>
            )}
            <Box
              style={{
                fontSize: "8pt",
                flexGrow: 1,
                opacity: 0.7,
              }}
            >
              SETTINGS
            </Box>

            <Box
              style={{
                fontSize: "8pt",
                width: "auto",
                textAlign: "right",
                display: "flex",
                alignItems: "center",
                pointerEvents: "auto",
              }}
            >
              {taskDescription ? (
                <ConductorTooltip
                  title={`${taskType} Task`}
                  content={taskDescription}
                  placement="left"
                  children={
                    <Link
                      href={
                        getTaskDocUrl(taskType) || getTaskDocUrl(TaskType.HTTP)
                      }
                      tabIndex={-1}
                      target="_blank"
                      rel="noreferrer"
                      style={{
                        display: "flex",
                        alignItems: "center",
                        fontSize: "9pt",
                        fontWeight: 500,
                        textDecoration: "none",
                      }}
                    >
                      <DocsIcon color={colors.blueLightMode} />
                      <div
                        style={{
                          marginLeft: "4px",
                          color: colors.blueLightMode,
                        }}
                      >
                        {truncate(taskType)} Docs
                      </div>
                    </Link>
                  }
                />
              ) : (
                <Link
                  href={getTaskDocUrl(taskType) || getTaskDocUrl(TaskType.HTTP)}
                  tabIndex={-1}
                  target="_blank"
                  rel="noreferrer"
                  style={{
                    display: "flex",
                    alignItems: "center",
                    fontSize: "9pt",
                    fontWeight: 500,
                    textDecoration: "none",
                  }}
                >
                  <DocsIcon color={colors.blueLightMode} />
                  <div
                    style={{
                      marginLeft: "4px",
                      color: colors.blueLightMode,
                    }}
                  >
                    {taskTypeLabel}
                    {" Docs"}
                  </div>
                </Link>
              )}
              {taskType !== TaskType.JOIN && (
                <Box pl={3}>
                  <OpenTestTaskButton
                    task={task}
                    tasksList={tasksList}
                    maxHeight={500}
                    disabled={isTrialExpired}
                  />
                </Box>
              )}
            </Box>
          </Box>
          <TaskFormHeader taskFormHeaderActor={taskFormHeaderActor} />
          <Box>
            <MaybeVariable
              value={task?.inputParameters}
              onChange={onChangeRequest}
              path={"inputParameters"}
              taskType={taskType}
            >
              <TaskForType />
            </MaybeVariable>
          </Box>
          {isUnknownTaskType && (
            <TaskFormSection>
              <Optional
                onChange={handleTaskFieldUpdate("optional")}
                taskJson={task}
              />
              <ConductorCacheOutput
                onChange={handleTaskFieldUpdate("cacheConfig")}
                taskJson={task}
              />
            </TaskFormSection>
          )}
        </Box>
        {ENABLE_TASK_STATS && <TaskStats />}
        <BoundaryTimerSection />
        {/*<TaskFormFooter {...{ selectedNode, onChange }} />*/}
      </Box>
    </Box>
  );
};

export default TaskFormContent;
