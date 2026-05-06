import { useInterpret, useSelector } from "@xstate/react";
import {
  FlowActionTypes,
  SelectNodeEvent,
} from "components/features/flow/state";
import { selectNodes } from "components/features/flow/state/selectors";
import { MessageContext } from "components/providers/messageContext";
import _isEmpty from "lodash/isEmpty";
import { useContext, useEffect } from "react";
import { useNavigate, useParams } from "react-router";
import { useQueryState } from "react-router-use-location-state";
import { NodeData } from "reaflow";
import { ExecutionTask, TaskStatus } from "types";
import {
  RUN_WORKFLOW_URL,
  SCHEDULER_DEFINITION_URL,
} from "utils/constants/route";
import { featureFlags, FEATURES } from "utils/flags";
import { useAuthHeaders, useCurrentUserInfo } from "utils/query";
import { taskWithLatestIteration } from "../helpers";
import {
  RightPanelContextEventTypes,
  SetSelectedTaskEvent,
} from "../RightPanel";
import { executionMachine } from "./machine";
import {
  ExecutionActionTypes,
  ExecutionTabs,
  UpdateQueryParamEvent,
} from "./types";

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

export const useExecutionMachine = () => {
  const authHeaders = useAuthHeaders();
  const { setMessage } = useContext(MessageContext);
  const navigate = useNavigate();
  const { data: currentUserInfo } = useCurrentUserInfo();

  const [tabIndex, setTabIndex] = useQueryState("tab", "");
  const [taskReferenceName, handleTaskReferenceName] = useQueryState<string>(
    "taskReferenceName",
    "",
  );
  const [taskId, handleTaskId] = useQueryState<string>("taskId", "");

  const service = useInterpret(executionMachine, {
    ...(process.env.NODE_ENV === "development" ? { devTools: true } : {}),
    context: {
      duration: isPlayground ? 10 : 30,
      authHeaders,
      currentUserInfo,
      selectedTaskReferenceName: taskReferenceName,
      selectedTaskId: taskId,
    },
    actions: {
      setErrorMessage: (context, event: any) => {
        setMessage({
          severity: "error",
          text: event?.data?.errorDetails?.message,
        });
      },
      updateQueryParam: (_context, data: UpdateQueryParamEvent) => {
        handleTaskReferenceName(data?.taskReferenceName || "");
      },
      setQueryParam: ({ execution }, { node }: SelectNodeEvent) => {
        const taskRefName = node?.data?.task?.taskReferenceName;
        handleTaskReferenceName(taskRefName || "");
        const executionStatus = node?.data?.task?.executionData?.status;
        if (executionStatus !== TaskStatus.PENDING) {
          const selectedTask = taskWithLatestIteration(
            execution?.tasks,
            taskRefName,
          );
          handleTaskId(selectedTask?.taskId || "");
        }
      },
      setTaskIdQueryParam: (_context, data: SetSelectedTaskEvent) => {
        if (data?.selectedTask?.taskId) {
          handleTaskId(data?.selectedTask?.taskId);
        }
      },
      clearQueryParams: (_context) => {
        handleTaskId("");
        handleTaskReferenceName("");
      },
      // Badge count is now updated by HumanTasksPoller at its next poll cycle.
      notifyOnHumanTask: () => {},
    },
  });
  const params = useParams<{ id: string }>();
  const executionId = params?.id;

  const execution = useSelector(service, (state) => state.context.execution);
  const executionTasks = useSelector(
    service,
    (state) => state.context.execution?.tasks,
  );

  const flowChildActor = useSelector(
    service,
    (state) => state.context.flowChild,
  );

  const nodes = useSelector(flowChildActor!, selectNodes);
  const maybeError = useSelector(service, (state) => state.context.error);
  const maybeMessage = useSelector(service, (state) => state.context.message);

  const send = service.send;
  useEffect(() => {
    if (executionId) {
      send({
        type: ExecutionActionTypes.UPDATE_EXECUTION,
        executionId,
      });
    }
  }, [executionId, send]);

  const changeExecutionTab = (tab: ExecutionTabs) => {
    setTabIndex(tab);
  };

  const openedTab = useSelector(service, (state) => state.context.currentTab);

  const isReady = useSelector(service, (state) => state.matches("init"));

  const isNoAccess = useSelector(service, (state) => state.matches("noAccess"));

  useEffect(() => {
    if (!_isEmpty(tabIndex) && openedTab !== tabIndex && isReady) {
      send({
        type: ExecutionActionTypes.CHANGE_EXECUTION_TAB,
        tab: tabIndex as ExecutionTabs,
      });
    }
  }, [tabIndex, openedTab, send, isReady]);

  const refetch = () => send({ type: ExecutionActionTypes.REFETCH });
  const closeRightPanel = () => {
    send({
      type: ExecutionActionTypes.CLOSE_RIGHT_PANEL,
    });
  };

  const selectTask = (taskSel: { ref?: string; taskId?: string }) => {
    const maybeSelectedTask = executionTasks?.find(
      (task: ExecutionTask) =>
        task.taskId === taskSel.taskId ||
        task.referenceTaskName === taskSel.ref,
    );
    if (maybeSelectedTask) {
      send({
        type: RightPanelContextEventTypes.SET_SELECTED_TASK,
        selectedTask: maybeSelectedTask,
      });
    } else {
      closeRightPanel();
    }
  };

  const expandDynamic = (taskReferenceName: string) =>
    send({ type: ExecutionActionTypes.EXPAND_DYNAMIC_TASK, taskReferenceName });

  const collapseDynamic = (taskReferenceName: string) =>
    send({
      type: ExecutionActionTypes.COLLAPSE_DYNAMIC_TASK,
      taskReferenceName,
    });

  const clearError = () =>
    send({
      type: ExecutionActionTypes.CLEAR_ERROR,
    });

  const resumeExecution = () => {
    send({
      type: ExecutionActionTypes.RESUME_EXECUTION,
    });
  };

  const pauseExecution = () => {
    send({
      type: ExecutionActionTypes.PAUSE_EXECUTION,
    });
  };

  const terminateExecution = () => {
    send({
      type: ExecutionActionTypes.TERMINATE_EXECUTION,
    });
  };

  const rerunExecutionWithLatestDefinitions = () => {
    navigate(RUN_WORKFLOW_URL, { state: { execution } });
  };

  const createSheduleWithLatestDefinitions = () => {
    navigate(SCHEDULER_DEFINITION_URL.NEW, { state: { execution } });
  };

  const restartExecutionWithLatestDefinitions = () => {
    send({
      type: ExecutionActionTypes.RESTART_EXECUTION,
      options: {
        useLatestDefinitions: "true",
      },
    });
  };

  const restartExecutionWithCurrentDefinitions = () => {
    send({
      type: ExecutionActionTypes.RESTART_EXECUTION,
      options: {},
    });
  };

  const retryExcutionFromFailed = () => {
    send({
      type: ExecutionActionTypes.RETRY_EXECUTION,
      options: {
        resumeSubworkflowTasks: "false",
      },
    });
  };

  const retryResumeSubworkflow = () => {
    send({
      type: ExecutionActionTypes.RETRY_EXECUTION,
      options: {
        resumeSubworkflowTasks: "true",
      },
    });
  };
  const updateDuration = (duration: number) => {
    send({
      type: ExecutionActionTypes.UPDATE_DURATION,
      duration,
    });
  };

  const handleUpdateVariables = (data: string) => {
    send({
      type: ExecutionActionTypes.UPDATE_VARIABLES,
      data: data,
    });
  };

  const selectNode = (node: NodeData) => {
    if (flowChildActor) {
      flowChildActor.send({
        type: FlowActionTypes.SELECT_NODE_EVT,
        node,
      });
    }
  };

  // const selectTaskWithTaskRef = (node: NodeData, exactTaskRef: string) => {
  //   if (flowChildActor) {
  //     flowChildActor.send({
  //       type: FlowActionTypes.SELECT_TASK_WITH_TASK_REF,
  //       node,
  //       exactTaskRef,
  //     });
  //   }
  // };

  const executionStatusMap = useSelector(
    service,
    (state) => state.context.executionStatusMap,
  );

  const taskListActor = useSelector(
    service,
    (state) => state.children.taskListMachine,
  );

  const rightPanelActor = useSelector(
    service,
    (state) => state.children?.rightPanelMachine,
  );

  const doWhileSelection = useSelector(
    service,
    (state) => state.context.doWhileSelection,
  );

  const selectedTask = useSelector(
    service,
    (state) => state.context.selectedTask,
  );

  const isAssistantPanelOpen = useSelector(
    service,
    (state) => state.context.isAssistantPanelOpen,
  );

  const toggleAssistantPanel = () => {
    send({
      type: ExecutionActionTypes.TOGGLE_ASSISTANT_PANEL,
    });
  };

  const stateSelectors = {
    flowActor: flowChildActor,
    countdownActor: service?.children?.get("countdownMachine"),
    execution,
    executionId,
    isReady,
    executionStatusMap,
    maybeError,
    maybeMessage,
    openedTab,
    taskListActor,
    rightPanelActor,
    isNoAccess,
    doWhileSelection,
    nodes,
    isAssistantPanelOpen,
    selectedTask,
  };

  return [
    {
      refetch,
      selectTask,
      expandDynamic,
      collapseDynamic,
      clearError,
      rerunExecutionWithLatestDefinitions,
      createSheduleWithLatestDefinitions,
      restartExecutionWithLatestDefinitions,
      restartExecutionWithCurrentDefinitions,
      retryExcutionFromFailed,
      resumeExecution,
      terminateExecution,
      pauseExecution,
      retryResumeSubworkflow,
      changeExecutionTab,
      updateDuration,
      closeRightPanel,
      handleUpdateVariables,
      selectNode,
      toggleAssistantPanel,
    },
    stateSelectors,
  ] as const;
};
