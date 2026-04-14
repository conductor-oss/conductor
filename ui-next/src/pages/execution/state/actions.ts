import {
  assign,
  send,
  spawn,
  DoneInvokeEvent,
  forwardTo,
  sendTo,
  raise,
  ActorRef,
  pure,
} from "xstate";
import { executionToWorkflowDef } from "./executionMapper";
import { flowMachine } from "components/features/flow/state/machine";
import {
  FlowActionTypes,
  FlowEvents,
  ResetZoomPositionEvent,
  SelectTaskWithTaskRefEvent,
} from "components/features/flow/state/types";
import { TaskStatus, Execution, DoWhileSelection, ExecutionTask } from "types";
import { gtagAbstract, flattenGtagObject } from "utils";
import {
  UpdateWfDefinitionEvent,
  SelectNodeEvent,
} from "components/features/flow/state/types";
import {
  UpdateExecutionEvent,
  ExecutionMachineContext,
  ExpandDynamicTaskEvent,
  CollapseDynamicTaskEvent,
  ErrorSeverity,
  ClearErrorEvent,
  UpdateDurationEvent,
  ChangeExecutionTabEvent,
  ExecutionActionTypes,
  FetchForLogsEvent,
  ExecutionUpdatedEvent,
  PersistErrorEvent,
  UpdateVariablesEvent,
  MessageSeverity,
  SetDoWhileIterationEvent,
  UpdateSelectedTaskEvent,
  ToggleAssistantPanelEvent,
} from "./types";
import { RightPanelContextEventTypes, RightPanelEvents } from "../RightPanel";
import {
  findTaskFromExecutionStatusMapById,
  taskWithLatestIteration,
} from "../helpers";
import { NodeData } from "reaflow";

const selectTaskByTaskReferenceName = (
  { execution }: ExecutionMachineContext,
  taskReferenceName: string,
) => {
  return taskWithLatestIteration(execution?.tasks, taskReferenceName);
};

const pendingTaskSelection = (task: any) => {
  const result = {
    ...task.executionData,
    workflowTask: task,
  };
  return result;
};

const executionToExecutionStatusExpand = (
  executionDef: any,
  expandedDynamic: any,
  doWhileSelection?: DoWhileSelection[],
  selectedTask?: ExecutionTask,
) => {
  const [workflowDefinition, executionStatusMap] = executionToWorkflowDef(
    executionDef,
    expandedDynamic,
    doWhileSelection,
    selectedTask,
  );
  return {
    execution: executionDef,
    workflowDefinition,
    executionStatusMap,
  };
};

export const updateExecution = assign<
  ExecutionMachineContext,
  DoneInvokeEvent<Execution>
>((context, { data }) => {
  const expandedExecution = executionToExecutionStatusExpand(
    data,
    context.expandedDynamic,
    context.doWhileSelection,
  );
  return expandedExecution;
});

export const updateExecutionMap = assign<
  ExecutionMachineContext,
  DoneInvokeEvent<Execution>
>((context, _data) => {
  const expandedExecution = executionToExecutionStatusExpand(
    context.execution,
    context.expandedDynamic,
    context.doWhileSelection,
    context.selectedTask,
  );
  return expandedExecution;
});

export const instanciateFlow = assign({
  flowChild: (_ctx, _event) => spawn(flowMachine),
});

export const persistExecutionId = assign<
  ExecutionMachineContext,
  UpdateExecutionEvent
>({
  executionId: (__, { executionId }) => executionId,
});

export const sendResetZoomEventToFlow = sendTo<
  ExecutionMachineContext,
  ResetZoomPositionEvent,
  ActorRef<FlowEvents>
>(
  (context) => context.flowChild!,
  () => {
    return {
      type: FlowActionTypes.RESET_ZOOM_POSITION,
    };
  },
  { delay: 50 },
);

export const notifyFlowUpdates = send<
  ExecutionMachineContext,
  UpdateWfDefinitionEvent
>(
  (ctx) => {
    return {
      type: FlowActionTypes.UPDATE_WF_DEFINITION_EVT,
      workflow: ctx.workflowDefinition,
      showPorts: false,
      workflowExecutionStatus: ctx?.execution?.status,
    };
  },
  { to: (context) => context.flowChild! },
);
// Commenting out dont think we need this
/* export const selectNodeInFlow = send<ExecutionMachineContext, SelectNodeEvent>( */
/*   ({ selectedTask }) => { */
/*     return { */
/*       type: FlowActionTypes.SELECT_NODE_INTERNAL_EVT, */
/*       node: { id: selectedTask?.workflowTask?.taskReferenceName }, */
/*     }; */
/*   }, */
/*   { to: (context) => context.flowChild! } */
/* ); */

export const nodeToTaskSelectionToPanel = send<
  ExecutionMachineContext,
  SelectNodeEvent
>(
  (context, { node }) => {
    const selectedTask =
      node?.data?.task?.executionData?.status === TaskStatus.PENDING
        ? pendingTaskSelection(node?.data?.task)
        : selectTaskByTaskReferenceName(
            context,
            node?.data?.task?.taskReferenceName,
          );
    return {
      type: RightPanelContextEventTypes.SET_SELECTED_TASK,
      selectedTask,
    };
  },
  { to: "#_internal" },
); // maps the event to event. in the same cycle https://github.com/statelyai/xstate/discussions/1847

export const taskToTaskSelectionToPanel = send<
  ExecutionMachineContext,
  SelectTaskWithTaskRefEvent
>(
  (context, { node, exactTaskRef }) => {
    const maybeTask =
      context?.executionStatusMap && context?.executionStatusMap[node.id];
    const selectedTask = maybeTask?.loopOver?.find(
      (item) => item.referenceTaskName === exactTaskRef,
    );

    return {
      type: RightPanelContextEventTypes.SET_SELECTED_TASK,
      selectedTask,
    };
  },
  { to: "#_internal" },
);

type WrappedErrorMessage = {
  originalError: { status: number };
  errorDetails: { message: string };
};
export const assignError = assign<
  ExecutionMachineContext,
  DoneInvokeEvent<WrappedErrorMessage>
>({
  error: (context, { data: { originalError, errorDetails } }) => {
    switch (originalError.status) {
      case 403:
        return {
          severity: ErrorSeverity.ERROR,
          text: "You don't have permission to execute this action",
        };
      default:
        return {
          severity: ErrorSeverity.ERROR,
          text: errorDetails.message,
        };
    }
  },
});

export const persistFlowError = assign<
  ExecutionMachineContext,
  PersistErrorEvent
>({ error: (_, errorObject) => errorObject });

export const clearError = assign<ExecutionMachineContext, ClearErrorEvent>({
  error: (_context, _) => undefined,
  message: (_context, _) => undefined,
});

export const addToExpandedDynamic = assign<
  ExecutionMachineContext,
  ExpandDynamicTaskEvent
>({
  expandedDynamic: ({ expandedDynamic }, { taskReferenceName }) =>
    expandedDynamic.concat(taskReferenceName),
});

export const removeFromExpandedDynamic = assign<
  ExecutionMachineContext,
  CollapseDynamicTaskEvent
>({
  expandedDynamic: ({ expandedDynamic }, { taskReferenceName }) =>
    expandedDynamic.filter((n) => n !== taskReferenceName),
});

export const updateWorkflowDefinition = assign(
  ({
    execution,
    expandedDynamic,
    doWhileSelection,
    selectedTask,
  }: ExecutionMachineContext) => {
    return executionToExecutionStatusExpand(
      execution,
      expandedDynamic,
      doWhileSelection,
      selectedTask,
    );
  },
);

export const persistCurrentTab = assign<
  ExecutionMachineContext,
  ChangeExecutionTabEvent
>({
  currentTab: (_context, { tab }) => tab,
});

export const updateExecutionDuration = assign<
  ExecutionMachineContext,
  UpdateDurationEvent
>((__context, event) => {
  return {
    duration: event?.duration,
    countdownType: event?.countdownType,
    isDisabledCountdown: event?.isDisabled,
  };
});

export const gtagEventLogger = (
  context: ExecutionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, "event");
  const eventPrefix = `event_at_execution_${context?.executionId}_of_type_${event?.type}`;
  gtagAbstract(eventPrefix, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowDefinition?.name,
    user_performed_action: event?.type,
    ...flattenEvent,
  });
};
export const gtagErrorLogger = (
  context: ExecutionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, "event");
  const eventPrefix = `error_at_execution_${context?.executionId}_of_type_${event?.type}`;
  gtagAbstract(eventPrefix, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowDefinition?.name,
    user_performed_action: event?.type,
    ...flattenEvent,
  });
};
export const startRenderingGtag = (
  context: ExecutionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, `event`);
  const prefix = `event_at_execution_${context?.executionId}_start_rendering_diagram_request`;
  gtagAbstract(prefix, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowDefinition?.name,
    user_performed_action: event?.type,
    start_time: new Date().getTime(),
    ...flattenEvent,
  });
};

export const finishRenderingGtag = (
  context: ExecutionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, `event`);
  const prefix = `event_at_execution_${context?.executionId}_finish_rendering_diagram_request}`;
  gtagAbstract(prefix, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowDefinition?.name,
    user_performed_action: event?.type,
    end_time: new Date().getTime(),
    ...flattenEvent,
  });
};

export const fetchForLogs = send<ExecutionMachineContext, FetchForLogsEvent>(
  (_context, _event) => {
    return {
      type: ExecutionActionTypes.FETCH_FOR_LOGS,
    };
  },
);
export const sendUpdatedExecution = sendTo<
  ExecutionMachineContext,
  ExecutionUpdatedEvent,
  ActorRef<RightPanelEvents>
>("rightPanelMachine", (context) => ({
  type: RightPanelContextEventTypes.SET_UPDATED_EXECUTION,
  execution: context.execution!,
  executionStatusMap: context.executionStatusMap!,
}));

export const forwardSelectionToPanel = forwardTo("rightPanelMachine");

export const raiseExecutionUpdated = raise(
  ExecutionActionTypes.EXECUTION_UPDATED,
);

export const persistSuccessUpdateVariablesMessage = assign<
  ExecutionMachineContext,
  DoneInvokeEvent<UpdateVariablesEvent>
>({
  message: (_context, _event) => {
    return {
      severity: MessageSeverity.SUCCESS,
      text: "Variables updated successfully.",
    };
  },
});

export const persistDoWhileIteration = assign(
  (
    { doWhileSelection }: ExecutionMachineContext,
    { data }: SetDoWhileIterationEvent,
  ) => {
    const updatedDoWhileSelection = [...(doWhileSelection ?? [])];
    const index = doWhileSelection?.findIndex(
      (item) => item.doWhileTaskReferenceName === data.doWhileTaskReferenceName,
    );
    if (index != null && index !== -1) {
      updatedDoWhileSelection[index] = data;
    } else {
      updatedDoWhileSelection.push(data);
    }
    return {
      doWhileSelection: updatedDoWhileSelection,
    };
  },
);

export const updateSelectedTask = assign(
  (_context, data: UpdateSelectedTaskEvent) => {
    return {
      selectedTask: data.selectedTask,
    };
  },
);

export const toggleAssistantPanel = assign<
  ExecutionMachineContext,
  ToggleAssistantPanelEvent
>({
  isAssistantPanelOpen: (context) => !context.isAssistantPanelOpen,
});

export const closeAssistantPanel = assign<ExecutionMachineContext>({
  isAssistantPanelOpen: () => false,
});

export const delayedNodeSelection = pure((ctx: ExecutionMachineContext) => {
  const identifyNodeTobeSelected = (
    maybeSelectedTask?: ExecutionTask,
    maybeSelectedNodeUsingTaskReference?: { id?: string },
  ) => {
    const taskReferenceNameFromMaybeSelectedTask =
      maybeSelectedTask?.workflowTask?.taskReferenceName;
    const taskReferenceNameFromMaybeSelectedNodeUsingTaskReference =
      maybeSelectedNodeUsingTaskReference?.id;
    if (
      taskReferenceNameFromMaybeSelectedTask ===
      taskReferenceNameFromMaybeSelectedNodeUsingTaskReference
    ) {
      return {
        nodeRef: taskReferenceNameFromMaybeSelectedTask,
        exactTaskRef: maybeSelectedTask?.referenceTaskName,
      };
    } else if (!maybeSelectedTask && maybeSelectedNodeUsingTaskReference) {
      return {
        nodeRef: taskReferenceNameFromMaybeSelectedNodeUsingTaskReference,
        exactTaskRef: taskReferenceNameFromMaybeSelectedNodeUsingTaskReference,
      };
    } else {
      return {
        nodeRef: taskReferenceNameFromMaybeSelectedTask,
        exactTaskRef: maybeSelectedTask?.referenceTaskName,
      };
    }
  };
  let selectedTaskReferenceName = ctx.selectedTaskReferenceName;
  if (
    ctx?.executionStatusMap &&
    (ctx?.selectedTaskId || ctx?.selectedTaskReferenceName)
  ) {
    const maybeSelectedTask = findTaskFromExecutionStatusMapById(
      ctx?.executionStatusMap,
      ctx?.selectedTaskId ?? "",
    );

    const { nodeRef, exactTaskRef } = identifyNodeTobeSelected(
      maybeSelectedTask!,
      { id: maybeSelectedTask?.workflowTask?.taskReferenceName },
    );
    if (exactTaskRef && nodeRef !== exactTaskRef) {
      return [
        sendTo(ctx.flowChild!, {
          type: FlowActionTypes.SELECT_TASK_WITH_TASK_REF,
          node: {
            id: maybeSelectedTask?.workflowTask?.taskReferenceName,
          } as NodeData,
          exactTaskRef,
        }),
        send((_context, _event) => {
          return {
            type: ExecutionActionTypes.UPDATE_QUERY_PARAM,
            taskReferenceName:
              maybeSelectedTask?.workflowTask?.taskReferenceName,
          };
        }),
      ];
    }
    if (maybeSelectedTask) {
      return [
        sendTo(
          ctx.flowChild!,
          {
            type: FlowActionTypes.SELECT_NODE_EVT,
            node: {
              id: maybeSelectedTask?.workflowTask.taskReferenceName,
            },
          },
          {
            delay: 150,
            id: "debounce_delayed_node_selection",
          },
        ),
      ];
    }
  }
  const selectedTask =
    ctx.selectedTaskId &&
    ctx.execution?.tasks?.find((t) => t.taskId === ctx.selectedTaskId);

  // If reference name is not set, use the task id to get the reference name
  if (!ctx.selectedTaskReferenceName && selectedTask) {
    selectedTaskReferenceName = selectedTask.workflowTask.taskReferenceName;
  }

  // This will prevent opening the right panel for wrong reference name
  const selectedTaskExists =
    (ctx.execution?.tasks ?? []).filter(
      (t) => t.workflowTask.taskReferenceName === selectedTaskReferenceName,
    ).length > 0;

  return selectedTaskExists
    ? [
        sendTo(
          ctx.flowChild!,
          {
            type: FlowActionTypes.SELECT_NODE_EVT,
            node: {
              id: selectedTaskReferenceName!,
            },
          },
          {
            delay: 150,
            id: "debounce_delayed_node_selection",
          },
        ),
      ]
    : [];
});
