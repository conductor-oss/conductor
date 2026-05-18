import { FlowActionTypes, FlowEvents } from "components/features/flow/state";
import _first from "lodash/first";
import _has from "lodash/has";
import _isNil from "lodash/isNil";
import _last from "lodash/last";
import { newWorkflowTemplate } from "templates/JSONSchemaWorkflow";
import { WorkflowDef } from "types/WorkflowDef";
import {
  adjust,
  defaultValueFromSchema,
  flattenGtagObject,
  getSequentiallySuffix,
  gtagAbstract,
  logger,
} from "utils";
import { ActorRef, assign, DoneInvokeEvent, forwardTo } from "xstate";
import { choose, log, pure, raise, sendTo } from "xstate/lib/actions";
import {
  ErrorInspectorEventTypes,
  ErrorInspectorMachineEvents,
  WorkflowWithNoErrorsEvent,
} from "../errorInspector/state";
import { CODE_TAB, RUN_TAB, SEVERITY_ERROR, TASK_TAB } from "./constants";
import {
  ADD_NEW_SWITCH_PATH,
  performOperation as applyWorkflowOperation,
  DELETE_TASK,
  moveTask,
  positionIdentifier,
  REMOVE_BRANCH,
  REPLACE_TASK,
} from "./taskModifier";
import {
  AddNewSwitchTaskEvent,
  ChangeTabEvent,
  ChangeVersionEvent,
  DefinitionMachineContext,
  DefinitionMachineEventTypes,
  DeleteRequestEvent,
  DONT_SHOW_IMPORT_SUCCESSFUL_DIALOG_TUTORIAL_AGAIN,
  HandleLeftPanelExpandedEvent,
  HandleSaveAndCreateNewEvent,
  HandleSaveAndRunEvent,
  LeftPaneTabs,
  MoveTaskEvent,
  PerformOperationEvent,
  RemoveBranchFromTaskEvent,
  RemoveTaskEvent,
  ReplaceTaskEvent,
  ResetRequestEvent,
  SaveAndCreateNewRequestEvent,
  SaveAndRunRequestEvent,
  SaveAsNewVersionRequestEvent,
  SyncRunContextAndChangeTabEvent,
  ToggleAgentExpandedEvent,
  UpdateAttributesEvent,
  UpdateWorkflowMetadataEvent,
} from "./types";

import { JsonSchema } from "@jsonforms/core";
import { crumbsToTask } from "components/features/flow/nodes";
import _isEmpty from "lodash/isEmpty";
import { CommonTaskDef } from "types/TaskType";
import { ImportSummary } from "utils/cloudTemplates";
import { SWITCH_CASE_PREFIX } from "utils/constants/switch";
import {
  LocalCopyMachineEventTypes,
  UseLocalCopyChangesEvent,
} from "../ConfirmLocalCopyDialog/state";
import { SavedSuccessfulEvent } from "../confirmSave/state";
import {
  CodeMachineEventTypes,
  HighlightTextReferenceEvent,
} from "../EditorPanel/CodeEditorTab/state";
import {
  FormMachineActionTypes,
  TaskFormEvents,
} from "../EditorPanel/TaskFormTab/state";
import { RunMachineEvents, RunMachineEventsTypes } from "../RunWorkflow/state";
import {
  WorkflowMetadataEvents,
  WorkflowMetadataMachineEventTypes,
} from "../WorkflowMetadata/state";
export const persistWorkflowAttribs = assign<
  DefinitionMachineContext,
  UpdateAttributesEvent
>(
  (
    context: DefinitionMachineContext,
    {
      isNewWorkflow,
      workflowName,
      currentVersion,
      workflowTemplateId,
      preserveWorkflowChanges,
    }: UpdateAttributesEvent,
  ) => {
    const newWorkflowDefinition: Partial<WorkflowDef> = newWorkflowTemplate(
      context.currentUserInfo?.id || "example@email.com",
    ) as unknown as Partial<WorkflowDef>;
    const currentWf = isNewWorkflow ? newWorkflowDefinition : {};

    // After a successful save the machine raises UPDATE_ATTRIBS_EVT to trigger a
    // version refetch. Without this guard `workflowChanges` would be cleared to {}
    // for the duration of that async fetch, causing the agent to see an empty
    // workflow and ask the user for task reference names it already knows.
    const workflowChanges =
      preserveWorkflowChanges && !isNewWorkflow && context.workflowChanges
        ? context.workflowChanges
        : currentWf;

    return {
      isNewWorkflow,
      workflowName,
      currentWf,
      workflowChanges,
      currentVersion,
      workflowTemplateId,
      // Keep agent collapsed by default to improve initial page load performance
      isAgentExpanded: context.isAgentExpanded ?? false,
    };
  },
);

export const updateWf = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{ workflow: WorkflowDef }>
>(({ workflowChanges }, { data }) => {
  return {
    currentWf: data?.workflow,
    // Because of reading workflow from local storage 1st
    workflowChanges: _isEmpty(workflowChanges)
      ? data?.workflow
      : workflowChanges,
  };
});

export const updateWfDefaultRunParam = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{ schema: JsonSchema }>
>((_context, { data }) => {
  const sanitizedDefaults = defaultValueFromSchema(data?.schema);
  return {
    workflowDefaultRunParam: sanitizedDefaults,
  };
});

export const updateSecretsAndEnvs = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{
    secrets: Record<string, unknown>[];
    envs: Record<string, unknown>;
  }>
>((_, { data }) => {
  return {
    secrets: data.secrets,
    envs: data.envs,
  };
});

export const resetChanges = assign<DefinitionMachineContext, any>({
  workflowChanges: ({ currentWf }, __) => currentWf,
});

export const updateCollapseWorkflowList = assign<DefinitionMachineContext, any>(
  (context, event) => {
    return {
      collapseWorkflowList: event?.collapseWorkflowList,
    };
  },
);

export const setVersion = assign<DefinitionMachineContext, ChangeVersionEvent>({
  currentVersion: (_currentVersion, { version }) => version,
});

export const resetCurrentVersion = assign((_ctx, _event) => {
  return {
    currentVersion: null,
  };
});

export const setMessage = assign({
  message: (_ctx, messageObj) => messageObj,
});

export const processErrorFetching = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{ message: string }>
>((__, { data }) => ({
  message: {
    text: data.message,
    severity: SEVERITY_ERROR,
  },
}));

export const resetMessage = assign((_ctx, __evt) => {
  return {
    message: {
      text: undefined,
      severity: undefined,
    },
  };
});

export const notifyFlowUpdates = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>("flowMachine", (ctx) => {
  return {
    type: FlowActionTypes.UPDATE_WF_DEFINITION_EVT,
    workflow: ctx.workflowChanges,
    cleanNodeSelection: ctx.selectedTaskCrumbs.length === 0,
  };
});

// Special version for agent updates that reads workflow directly from event
// instead of context, to avoid XState assign timing issues
export const notifyFlowUpdatesFromEvent = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>("flowMachine", (ctx, event) => {
  return {
    type: FlowActionTypes.UPDATE_WF_DEFINITION_EVT,
    workflow: event.workflow,
    cleanNodeSelection: ctx.selectedTaskCrumbs.length === 0,
  };
});

export const forwardCollapseWorkflowList = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>("flowMachine", (ctx, event) => {
  return {
    type: FlowActionTypes.UPDATE_COLLAPSE_WORKFLOW_LIST,
    workflowName: event.workflowName,
  };
});

export const notifyFlowResetZoomPosition = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>("flowMachine", (_ctx) => {
  return {
    type: FlowActionTypes.RESET_ZOOM_POSITION,
  };
});

export const setFlowAsReadOnly = sendTo("flowMachine", (_ctx) => {
  return {
    type: FlowActionTypes.SET_READ_ONLY_EVT,
  };
});

export const changeTab = assign<
  DefinitionMachineContext,
  ChangeTabEvent | SyncRunContextAndChangeTabEvent
>({
  openedTab: (_context, event) =>
    "data" in event ? event.data.originalEvent.tab : event.tab,
  previousTab: ({ openedTab }, _) => openedTab,
  isAgentExpanded: (context, event) => {
    const nextTab = "data" in event ? event.data.originalEvent.tab : event.tab;
    return nextTab === CODE_TAB ? false : context.isAgentExpanded;
  },
});

export const changeToCodeTab = assign({
  openedTab: CODE_TAB,
  previousTab: ({ openedTab }: DefinitionMachineContext, _event) => openedTab,
  isAgentExpanded: false,
});

export const changeToTaskTab = assign({
  openedTab: TASK_TAB,
  previousTab: ({ openedTab }: DefinitionMachineContext, _event) => openedTab,
});

export const changeToPreviousTab = assign<DefinitionMachineContext>({
  openedTab: ({ previousTab, openedTab }: DefinitionMachineContext) =>
    previousTab === openedTab ? LeftPaneTabs.WORKFLOW_TAB : previousTab,
});

export const performOperation = assign<
  DefinitionMachineContext,
  PerformOperationEvent
>({
  workflowChanges: (context, { data, operation }) => {
    const workflowAfterChanges = applyWorkflowOperation({
      workflow: context.workflowChanges,
      crumbs: data.crumbs,
      taskDef: data.task,
      operation: {
        type: data.action,
        ...operation,
      },
    });
    return workflowAfterChanges;
  },
});

export const replaceTask = assign<DefinitionMachineContext, ReplaceTaskEvent>({
  workflowChanges: (context, { task, crumbs, newTask }) => {
    const workflowAfterChanges = applyWorkflowOperation({
      workflow: context?.workflowChanges,
      crumbs,
      taskDef: task,
      operation: {
        type: REPLACE_TASK,
        payload: newTask,
      },
    });
    return workflowAfterChanges;
  },
});

// export const cancelDebounceEditChanges = cancel("debounce_edit_event");

export const removeTask = assign<DefinitionMachineContext, RemoveTaskEvent>({
  workflowChanges: (context, { task, crumbs }) => {
    const workflowAfterChanges = applyWorkflowOperation({
      workflow: context?.workflowChanges,
      crumbs,
      taskDef: task,
      operation: {
        type: DELETE_TASK,
        payload: {},
      },
    });
    return workflowAfterChanges;
  },
});

export const addNewSwitchStatementToTask = assign<
  DefinitionMachineContext,
  AddNewSwitchTaskEvent
>({
  workflowChanges: (context, { task, crumbs }) => {
    const currentPathNames = task.decisionCases
      ? Object.keys(task.decisionCases)
      : [];

    const workflowAfterChanges = applyWorkflowOperation({
      workflow: context?.workflowChanges,
      crumbs,
      taskDef: task,
      operation: {
        type: ADD_NEW_SWITCH_PATH,
        payload: {
          branchName: getSequentiallySuffix({
            name: SWITCH_CASE_PREFIX,
            refNames: currentPathNames,
          }).name,
        },
      },
    });
    return workflowAfterChanges;
  },
});

export const removeBranchFromTask = assign<
  DefinitionMachineContext,
  RemoveBranchFromTaskEvent
>({
  workflowChanges: (context) => {
    const { workflowChanges, lastRemovalOperation } = context;
    const { crumbs, task, branchName } = lastRemovalOperation!;
    const workflowAfterChanges = applyWorkflowOperation({
      workflow: workflowChanges,
      crumbs,
      taskDef: task,
      operation: {
        type: REMOVE_BRANCH,
        payload: {
          branchName,
        },
      },
    });
    return workflowAfterChanges;
  },
});

export const updateWFMetadata = assign<
  DefinitionMachineContext,
  UpdateWorkflowMetadataEvent
>({
  workflowChanges: (context, { workflowMetadata }) => {
    const updatedWf: Partial<WorkflowDef> = {
      ...context.workflowChanges,
      ...workflowMetadata,
    };
    return updatedWf;
  },
});

export const forwardToCodeMachine = forwardTo("codeMachine");

export const forwardToSaveMachine = forwardTo("saveChangesMachine");

export const selectNewTask = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>("flowMachine", ({ lastPerformedOperation: operation }, _) => {
  return {
    type: FlowActionTypes.SELECT_NODE_EVT,
    node: {
      id: (Array.isArray(operation?.payload)
        ? (_first(operation?.payload) as CommonTaskDef | undefined)
            ?.taskReferenceName
        : operation?.payload?.taskReferenceName)!,
    },
  };
});

export const cleanLastOperation = assign({
  lastPerformedOperation: undefined,
});

export const cleanTaskCrumbSelection = assign<DefinitionMachineContext>({
  selectedTaskCrumbs: [],
});

export const updateSelectedCrumbs = assign<
  DefinitionMachineContext,
  ReplaceTaskEvent
>({
  selectedTaskCrumbs: ({ selectedTaskCrumbs }, { newTask }) => {
    return adjust(
      selectedTaskCrumbs.length - 1,
      () => ({
        ..._last(selectedTaskCrumbs),
        ref: newTask.taskReferenceName,
      }),
      selectedTaskCrumbs,
    );
  },
});

export const persistLastOperation = assign<
  DefinitionMachineContext,
  PerformOperationEvent
>({
  lastPerformedOperation: (context, event) => {
    return event.operation;
  },
});

export const validateWorkflow = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<ErrorInspectorMachineEvents>
>("errorInspectorMachine", ({ workflowChanges }) => {
  return {
    type: ErrorInspectorEventTypes.VALIDATE_WORKFLOW,
    workflow: workflowChanges || {},
  };
});

export const forwardCleanWorkflow = sendTo<
  DefinitionMachineContext,
  WorkflowWithNoErrorsEvent,
  ActorRef<FlowEvents>
>("flowMachine", (_ctx, { workflow }) => {
  return {
    type: FlowActionTypes.UPDATE_WF_DEFINITION_EVT,
    workflow,
    cleanNodeSelection: false,
  };
});

export const sendCrumbUpdates = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<TaskFormEvents>
>("formTaskMachine", (ctx) => {
  return {
    type: FormMachineActionTypes.UPDATE_CRUMBS,
    crumbs: ctx.selectedTaskCrumbs,
    task: crumbsToTask(
      ctx.selectedTaskCrumbs,
      ctx.workflowChanges?.tasks || [],
    ),
  };
});

export const persistSelectedTabCrumbs = assign<DefinitionMachineContext, any>(
  (_, event) => {
    return {
      selectedTaskCrumbs: event?.node?.data?.crumbs,
    };
  },
);

export const forwardToErrorInspector = forwardTo("errorInspectorMachine");

export const forwardSelectEdge = forwardTo("formTaskMachine");

export const logStuff = (context: DefinitionMachineContext, event: any) => {
  logger.error("[Error]: This is the context", context);
  logger.error("[Error]: This is the event", event);
};

export const startRenderingGtag = (
  context: DefinitionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, `event`);
  const prefix = `event_at_workflow_${context?.workflowName}_start_rendering_diagram_request`;
  gtagAbstract(prefix, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowName,
    user_performed_action: event?.type,
    start_time: new Date().getTime(),
    ...flattenEvent,
  });
};

export const gtagEventLogger = (
  context: DefinitionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, `event`);
  const prefix = `event_at_workflow_${context?.workflowName}_of_type_${event?.type}`;
  gtagAbstract(prefix, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowName,
    user_performed_action: event?.type,
    ...flattenEvent,
  });
};
export const gtagErrorLogger = (
  context: DefinitionMachineContext,
  event: any,
) => {
  const flattenEvent = flattenGtagObject(event, "event");
  gtagAbstract(`error_${context?.workflowName}_${event?.type}`, {
    user_uuid: context.currentUserInfo?.uuid,
    workflow_name: context?.workflowName,
    user_performed_action: event?.type,
    ...flattenEvent,
  });
};

export const cleanServerErrors = sendTo(
  "errorInspectorMachine",
  (__context, _event) => {
    return {
      type: ErrorInspectorEventTypes.CLEAN_SERVER_ERRORS,
    };
  },
);

export const cleanRunErrors = sendTo(
  "errorInspectorMachine",
  (__context, _event) => {
    return {
      type: ErrorInspectorEventTypes.CLEAN_RUN_ERRORS,
    };
  },
);

export const persistWorkflowChanges = assign<DefinitionMachineContext, any>(
  ({ workflowChanges }, { workflow }) => {
    if (_isNil(workflow)) {
      logger.info(
        "persistWorkflowChanges: incoming workflow is null so staying with context changes.",
      );

      return {
        workflowChanges,
      };
    }

    return {
      workflowChanges: workflow,
    };
  },
);

export const sendWorkflowToInspector = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<ErrorInspectorMachineEvents>
>("errorInspectorMachine", ({ workflowChanges }) => {
  return {
    type: ErrorInspectorEventTypes.SET_WORKFLOW,
    workflow: workflowChanges || {},
  };
});

export const sendWorkflowChangesToMetadataMachine = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<WorkflowMetadataEvents>
>("workflowTabMetaEditor", ({ workflowChanges }) => {
  return {
    type: WorkflowMetadataMachineEventTypes.FORCE_WORKFLOW,
    workflow: workflowChanges || {},
  };
});

// Reads from event.workflow directly (not ctx.workflowChanges) to avoid XState
// v4 assign-before-sendTo timing: assign actions update context for the next
// state snapshot, so sendTo actions in the same transition still see the old
// context value.
export const sendWorkflowChangesToMetadataMachineFromEvent = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<WorkflowMetadataEvents>
>("workflowTabMetaEditor", (_ctx, event) => {
  return {
    type: WorkflowMetadataMachineEventTypes.FORCE_WORKFLOW,
    workflow: event.workflow || {},
  };
});

export const forwardWorkflowToCodeMachine = sendTo<
  DefinitionMachineContext,
  any,
  any
>("codeMachine", (_ctx, event) => {
  return {
    type: CodeMachineEventTypes.FORCE_WORKFLOW,
    workflow: event.workflow,
  };
});

export const notifyToFlowIfOutputParameters = choose<
  DefinitionMachineContext,
  UpdateWorkflowMetadataEvent
>([
  {
    cond: (_context, event) =>
      _has(event, "workflowMetadata.outputParameters") ||
      _has(event, "workflowMetadata.inputParameters"),
    actions: ["notifyFlowUpdates"],
  },
  {
    actions: [
      (__c) =>
        log("Nothing to notify. outputParameters have not been modified"),
    ],
  },
]);

export const persistRemovalOperation = assign<
  DefinitionMachineContext,
  RemoveBranchFromTaskEvent
>({
  lastRemovalOperation: (__context, { type: _type, ...rest }) => rest,
});

export const cleanLastRemovalOperation = assign<DefinitionMachineContext, any>({
  lastRemovalOperation: undefined,
});

export const applyLastRemovalOperationAsRemoveTaskOperation = assign<
  DefinitionMachineContext,
  any
>({
  workflowChanges: (context) => {
    const { lastRemovalOperation } = context;
    const { crumbs, task } = lastRemovalOperation!;
    const workflowAfterChanges = applyWorkflowOperation({
      workflow: context?.workflowChanges,
      crumbs,
      taskDef: task,
      operation: {
        type: DELETE_TASK,
        payload: {},
      },
    });
    return workflowAfterChanges;
  },
});

export const forwardWorkflowToLocalCopyMachine = forwardTo("localCopyMachine");

export const forwardWorkflowToMetadataEditorMachine = forwardTo(
  "workflowMetadataEditorMachine",
);

export const forwardWorkflowToTabMetadataEditorMachine = forwardTo(
  "workflowTabMetaEditor",
);

export const removeLocalCopy = raise<DefinitionMachineContext, any>({
  type: LocalCopyMachineEventTypes.REMOVE_LOCAL_COPY,
});

export const persistWorkflowNameAndVersion = assign<
  DefinitionMachineContext,
  SavedSuccessfulEvent
>({
  workflowName: ({ workflowName }, { workflowName: eventWfName }) =>
    eventWfName ? eventWfName : workflowName,
  currentVersion: (
    { currentVersion },
    { currentVersion: eventCurrentVersion },
  ) => (eventCurrentVersion ? `${eventCurrentVersion}` : currentVersion),
  currentWf: (_context, { workflow }) => workflow,
  workflowChanges: (_context, { workflow }) => workflow,
  isNewWorkflow: (_context, { isNewWorkflow }) => isNewWorkflow,
});

export const maybePersistLocalCopyMessage = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{
    workflow: Partial<WorkflowDef>;
    isLocalStorageEmpty?: boolean;
  }>
>({
  localCopyMessage: ({ isNewWorkflow }, event) => {
    return isNewWorkflow && !event.data?.isLocalStorageEmpty
      ? `Showing last unsaved workflow. `
      : undefined;
  },
});

export const moveTaskFromLocation = assign<
  DefinitionMachineContext,
  MoveTaskEvent
>({
  workflowChanges: (context, event) => {
    const {
      sourceTask,
      sourceTaskCrumbs,
      targetLocationCrumbs,
      targetTask,
      position,
    } = event;

    return moveTask({
      workflow: context?.workflowChanges,
      source: { task: sourceTask, crumbs: sourceTaskCrumbs },
      target: { crumbs: targetLocationCrumbs, task: targetTask },
      position: positionIdentifier(position),
    });
  },
});

export const selectMovedTask = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>(
  "flowMachine",
  (__context, { sourceTask }) => {
    return {
      type: FlowActionTypes.SELECT_NODE_EVT,
      node: {
        id: sourceTask?.taskReferenceName,
      },
    };
  },
  { delay: 100 },
);

export const reSelectTaskIfSelected = pure<DefinitionMachineContext, any>(
  ({ selectedTaskCrumbs }) => {
    const lastCrumb = _last(selectedTaskCrumbs);
    if (lastCrumb?.ref) {
      return sendTo<DefinitionMachineContext, any, ActorRef<FlowEvents>>(
        "flowMachine",
        (__context) => {
          return {
            type: FlowActionTypes.SELECT_NODE_EVT,
            node: {
              id: lastCrumb?.ref,
            },
          };
        },
        { delay: 100 },
      );
    }
  },
);

export const cleanLocalCopyMessage = assign<DefinitionMachineContext>({
  localCopyMessage: undefined,
});

export const updateWfFromLocalStorage = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<
    { workflow?: Partial<WorkflowDef> } | UseLocalCopyChangesEvent
  >
>((context, { data }) => {
  return {
    workflowChanges: data.workflow,
  };
});

export const fireChangeToWorkflowTab = raise<DefinitionMachineContext, any>({
  type: DefinitionMachineEventTypes.CHANGE_TAB_EVT,
  tab: LeftPaneTabs.WORKFLOW_TAB,
});

export const fireChangeToCodeTab = raise<DefinitionMachineContext, any>({
  type: DefinitionMachineEventTypes.CHANGE_TAB_EVT,
  tab: LeftPaneTabs.CODE_TAB,
});

export const fireChangeToRunTab = raise<DefinitionMachineContext, any>({
  type: DefinitionMachineEventTypes.CHANGE_TAB_EVT,
  tab: LeftPaneTabs.RUN_TAB,
});

export const fireChangeToDependenciesTab = raise<DefinitionMachineContext, any>(
  {
    type: DefinitionMachineEventTypes.CHANGE_TAB_EVT,
    tab: LeftPaneTabs.DEPENDENCIES_TAB,
  },
);

export const handleLeftPanelExpanded = raise<
  DefinitionMachineContext,
  HandleLeftPanelExpandedEvent
>({
  type: DefinitionMachineEventTypes.HANDLE_LEFT_PANEL_EXPANDED,
  onSelectNode: true,
});

export const persistCodeReference = assign<
  DefinitionMachineContext,
  HighlightTextReferenceEvent
>({
  codeTextReference: (_context, { reference }) => reference,
});

export const cleanCodeTextReference = assign<DefinitionMachineContext>({
  codeTextReference: undefined,
});

export const setRunTabAsPreviousTab = assign({
  previousTab: (_context, _event) => RUN_TAB,
});

export const fireSaveEvent = raise<
  DefinitionMachineContext,
  SaveAndRunRequestEvent
>({
  type: DefinitionMachineEventTypes.SAVE_EVT,
  isSaveAndRun: true,
});

export const fireSaveAndCreateNewRequestEvent = raise<
  DefinitionMachineContext,
  SaveAndCreateNewRequestEvent
>((__, event) => ({
  type: DefinitionMachineEventTypes.SAVE_EVT,
  originalEvent: event.type,
}));

export const raiseResetEvent = raise<
  DefinitionMachineContext,
  ResetRequestEvent
>(
  {
    type: DefinitionMachineEventTypes.RESET_EVT,
  },
  { delay: 200 },
);
export const raiseDeleteEvent = raise<
  DefinitionMachineContext,
  DeleteRequestEvent
>(
  {
    type: DefinitionMachineEventTypes.DELETE_EVT,
  },
  { delay: 200 },
);
export const raiseSaveEvent = raise<
  DefinitionMachineContext,
  SaveAsNewVersionRequestEvent
>(
  (__, { isNewVersion }) => ({
    type: DefinitionMachineEventTypes.SAVE_EVT,
    isNewVersion,
  }),
  { delay: 200 },
);

export const raiseSaveAndRunEvent = raise<
  DefinitionMachineContext,
  HandleSaveAndRunEvent
>(
  {
    type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN,
  },
  { delay: 200 },
);

export const justExecute = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<RunMachineEvents>
>(
  "runWorkflowMachine",
  () => {
    return {
      type: RunMachineEventsTypes.TRIGGER_RUN_WORKFLOW,
    };
  },
  { delay: 200 },
);

export const raiseSaveAndCreateNewEvent = raise<
  DefinitionMachineContext,
  HandleSaveAndCreateNewEvent
>(
  {
    type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW,
  },
  { delay: 200 },
);

export const maybeSelectInitialSelectedTaskReference = pure<
  DefinitionMachineContext,
  any
>(({ initialSelectedTaskReferenceName }) => {
  if (initialSelectedTaskReferenceName != null) {
    return sendTo<DefinitionMachineContext, any, ActorRef<FlowEvents>>(
      "flowMachine",
      (__context) => {
        return {
          type: FlowActionTypes.SELECT_NODE_EVT,
          node: {
            id: initialSelectedTaskReferenceName,
          },
        };
      },
      { delay: 50 },
    );
  }
});

export const cleanInitialSelectedTaskReferenceName = assign({
  initialSelectedTaskReferenceName: undefined,
});

export const setSaveSourceEventType = assign<
  DefinitionMachineContext,
  HandleSaveAndCreateNewEvent
>({
  saveSourceEventType: (_context, event) => event.type,
});

export const raiseUpdateAtribsEvent = raise<
  DefinitionMachineContext,
  UpdateAttributesEvent
>(
  (
    context: DefinitionMachineContext,
    {
      isNewWorkflow,
      workflowName,
      workflowVersions,
      currentVersion,
      workflowTemplateId,
    }: UpdateAttributesEvent,
  ) => {
    // Check if this is a "save and create new" operation by looking at the save source event type
    const isContinueCreate =
      context.saveSourceEventType ===
      DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW;

    // If this is a "save and create new" operation, use new workflow attributes
    if (isContinueCreate) {
      return {
        type: DefinitionMachineEventTypes.UPDATE_ATTRIBS_EVT,
        workflowName: "NEW", // This will be replaced by persistWorkflowAttribs
        isNewWorkflow: true,
        workflowVersions: [],
        currentVersion: undefined,
        workflowTemplateId: undefined,
      };
    }

    return {
      type: DefinitionMachineEventTypes.UPDATE_ATTRIBS_EVT,
      workflowName,
      isNewWorkflow,
      workflowVersions,
      currentVersion,
      workflowTemplateId,
      // Do NOT set preserveWorkflowChanges here. After a successful save the
      // machine re-fetches the workflow from the server; workflowChanges must
      // be reset to {} so that updateWf can sync it to the freshly-loaded
      // server copy. If we preserved the stale workflowChanges here,
      // fastDeepEqual(workflowChanges, currentWf) would fail (server adds
      // updateTime etc.) and the save button would stay permanently enabled.
    };
  },
);
export const persistWorkflowVersionsParsed = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{ versions: number[] }>
>((_context, { data: { versions } }) => {
  return {
    workflowVersions: versions,
  };
});

export const persistLatestVersion = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<{ versions: string[] }>
>((_context, { data: { versions } }) => {
  const latestVersion = _last(versions);
  return {
    currentVersion: latestVersion,
  };
});

export const markDontShowImportSuccessfulDialogAgain = () => {
  localStorage.setItem(
    DONT_SHOW_IMPORT_SUCCESSFUL_DIALOG_TUTORIAL_AGAIN,
    "true",
  );
};
export const showTaskDescriptions = sendTo<
  DefinitionMachineContext,
  any,
  ActorRef<FlowEvents>
>(
  "flowMachine",
  (__context) => {
    return {
      type: FlowActionTypes.TOGGLE_SHOW_DESCRIPTION,
    };
  },
  { delay: 100 },
);

export const persistImportSummary = assign<
  DefinitionMachineContext,
  DoneInvokeEvent<ImportSummary>
>({
  importSummary: (_context, { data }) => data,
});

export const reportErrorToErrorInspector = sendTo(
  ({ errorInspectorMachine }) => errorInspectorMachine,
  (_context, { data }: DoneInvokeEvent<{ message: string }>) => ({
    type: ErrorInspectorEventTypes.REPORT_SERVER_ERROR,
    text: data.message,
  }),
);

export const cleanSerializationError = sendTo(
  "errorInspectorMachine",
  (_context, _event) => {
    return {
      type: ErrorInspectorEventTypes.CLEAN_SERIALIZATION_ERROR,
    };
  },
);

export const updateRunTabFormState = assign<
  DefinitionMachineContext,
  SyncRunContextAndChangeTabEvent
>({
  runTabFormState: (_ctx, event) => event.data.runMachineContext,
});

export const toggleAgentExpanded = assign<
  DefinitionMachineContext,
  ToggleAgentExpandedEvent
>({
  isAgentExpanded: (context, event) => {
    if (event.expanded !== undefined) {
      return event.expanded;
    }
    return !context.isAgentExpanded;
  },
});

export const collapseAgent = assign<DefinitionMachineContext, any>({
  isAgentExpanded: false,
});

export const autoExpandAgentForNewWorkflow = assign<
  DefinitionMachineContext,
  any
>({
  isAgentExpanded: (context) => {
    // Auto-expand agent for new workflows after diagram loads
    return context.isNewWorkflow ? true : context.isAgentExpanded;
  },
});

export const forwardToRunWorkflowMachine = forwardTo("runWorkflowMachine");
