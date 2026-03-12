import { ActorRef, DoneInvokeEvent } from "xstate";
import {
  ErrorInspectorMachineEvents,
  WorkflowWithNoErrorsEvent,
} from "../errorInspector/state";
import { UseLocalCopyChangesEvent } from "../ConfirmLocalCopyDialog/state";
import {
  AuthHeaders,
  Crumb,
  TaskDef,
  WorkflowDef,
  WorkflowMetadataI,
  User,
} from "types";
import { FlowEvents } from "components/flow/state";
import { CodeTextReference } from "../EditorPanel/CodeEditorTab/state";
import {
  SavedCancelledEvent,
  SavedSuccessfulEvent,
} from "../confirmSave/state";
import { ImportSummary } from "utils/cloudTemplates";

type ImportantMessage = {
  text?: string;
  severity?: string;
};

export type OperationContext = {
  task: TaskDef;
  crumbs: Crumb[];
  action: string; // Not really a string
};

export enum LeftPaneTabs {
  WORKFLOW_TAB = 0,
  TASK_TAB = 1,
  CODE_TAB = 2,
  RUN_TAB = 3,
  DEPENDENCIES_TAB = 4,
}

export type RunWorkflowFields = {
  input?: string;
  correlationId?: string;
  taskToDomain?: string;
  idempotencyKey?: string;
  idempotencyStrategy?: any;
};

export enum DefinitionMachineEventTypes {
  UPDATE_ATTRIBS_EVT = "updateAttributes",
  SAVE_EVT = "save",
  RESET_EVT = "reset",
  DELETE_EVT = "delete",
  CHANGE_VERSION_EVT = "changeVersion",
  ASSIGN_MESSAGE_EVT = "assignMessage",
  MESSAGE_RESET_EVT = "messageReset",
  RESET_CONFIRM_EVT = "resetConfirm",
  CANCEL_EVENT_EVT = "cancel",
  DELETE_CONFIRM_EVT = "deleteConfirm",
  CHANGE_TAB_EVT = "changeTab",
  PERFORM_OPERATION_EVT = "performOperation",
  REPLACE_TASK_EVT = "replaceTask",
  DEBOUNCE_REPLACE_TASK_EVT = "debounceReplaceTask",
  REMOVE_TASK_EVT = "removeTask",
  ADD_NEW_SWITCH_PATH_EVT = "addNewSwitchPathToTask",
  UPDATE_WF_METADATA_EVT = "updateWorkflowMetadata",
  REMOVE_BRANCH_EVT = "removeBranch",
  TOGGLE_META_BAR_EDIT_MODE_EVT = "toggleMetaBarEditMode",
  FLOW_FINISHED_RENDERING = "FLOW_FINISHED_RENDERING",
  DOWNLOAD_FILE_REQUEST = "DOWNLOAD_FILE_REQUEST",
  CONFIRM_LAST_FORK_REMOVAL = "CONFIRM_LAST_FORK_REMOVAL",
  MOVE_TASK_EVT = "MOVE_TASK_EVT",
  HANDLE_LEFT_PANEL_EXPANDED = "HANDLE_LEFT_PANEL_EXPANDED",
  HANDLE_SAVE_AND_RUN = "HANDLE_SAVE_AND_RUN",
  REDIRECT_TO_EXECUTION_PAGE = "REDIRECT_TO_EXECUTION_PAGE",
  HANDLE_SAVE_AND_CREATE_NEW = "HANDLE_SAVE_AND_CREATE_NEW",
  EXECUTE_WF = "EXECUTE_WF",
  NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG = "NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG",
  DISMISS_IMPORT_SUCCESSFUL_DIALOG = "DISMISS_IMPORT_SUCCESSFUL_DIALOG",
  SYNC_RUN_CONTEXT_AND_CHANGE_TAB = "SYNC_RUN_CONTEXT_AND_CHANGE_TAB",
  COLLAPSE_SIDEBAR_AND_RIGHT_PANEL = "COLLAPSE_SIDEBAR_AND_RIGHT_PANEL",
  WORKFLOW_FROM_AGENT = "WORKFLOW_FROM_AGENT",
  TOGGLE_AGENT_EXPANDED = "TOGGLE_AGENT_EXPANDED",
}

export const DONT_SHOW_IMPORT_SUCCESSFUL_DIALOG_TUTORIAL_AGAIN =
  "dontShowImportSuccessfulDialogTutorialAgain:2";

export type PerformedOperation = {
  payload: Partial<TaskDef> | Partial<TaskDef>[];
};

export type ErrorOnInvokeEvent = DoneInvokeEvent<{
  originalError: { status: number };
  errorDetails: { message: string };
}>;

export type UpdateAttributesEvent = {
  type: DefinitionMachineEventTypes.UPDATE_ATTRIBS_EVT;
  isNewWorkflow: boolean;
  workflowName: string;
  workflowVersions?: number[];
  currentVersion?: string;
  workflowTemplateId?: string;
};

export type ChangeVersionEvent = {
  type: DefinitionMachineEventTypes.CHANGE_VERSION_EVT;
  version: string;
};

export type ChangeTabEvent = {
  type: DefinitionMachineEventTypes.CHANGE_TAB_EVT;
  tab: LeftPaneTabs;
};

export type PerformOperationEvent = {
  type: DefinitionMachineEventTypes.PERFORM_OPERATION_EVT;
  data: OperationContext;
  operation: PerformedOperation;
};

export type ReplaceTaskEvent = {
  type: DefinitionMachineEventTypes.REPLACE_TASK_EVT;
  task: TaskDef;
  crumbs: Crumb[];
  newTask: TaskDef;
};

export type RemoveTaskEvent = {
  type: DefinitionMachineEventTypes.REMOVE_TASK_EVT;
  task: TaskDef;
  crumbs: Crumb[];
};

export type AddNewSwitchTaskEvent = {
  type: DefinitionMachineEventTypes.ADD_NEW_SWITCH_PATH_EVT;
  task: TaskDef;
  crumbs: Crumb[];
};

type RemovalOperationPayload = {
  task: TaskDef;
  crumbs: Crumb[];
  branchName: string;
};

export type RemoveBranchFromTaskEvent = {
  type: DefinitionMachineEventTypes.REMOVE_BRANCH_EVT;
} & RemovalOperationPayload;

export type UpdateWorkflowMetadataEvent = {
  type: DefinitionMachineEventTypes.UPDATE_WF_METADATA_EVT;
  workflowMetadata: Partial<WorkflowMetadataI>;
};

export type DownloadFileEvent = {
  type: DefinitionMachineEventTypes.DOWNLOAD_FILE_REQUEST;
};

export type SaveRequestEvent = {
  type: DefinitionMachineEventTypes.SAVE_EVT;
};

export type SaveAndCreateNewRequestEvent = {
  type: DefinitionMachineEventTypes.SAVE_EVT;
  originalEvent: DefinitionMachineEventTypes;
};

export type HandleSaveAndRunEvent = {
  type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_RUN;
};

export type HandleSaveAndCreateNewEvent = {
  type: DefinitionMachineEventTypes.HANDLE_SAVE_AND_CREATE_NEW;
};

export type SaveAsNewVersionRequestEvent = {
  type: DefinitionMachineEventTypes.SAVE_EVT;
  isNewVersion: boolean;
};

export type SaveAndRunRequestEvent = {
  type: DefinitionMachineEventTypes.SAVE_EVT;
  isSaveAndRun: boolean;
};

export type ResetRequestEvent = {
  type: DefinitionMachineEventTypes.RESET_EVT;
};

export type ResetConfirmEvent = {
  type: DefinitionMachineEventTypes.RESET_CONFIRM_EVT;
};

export type DeleteConfirmEvent = {
  type: DefinitionMachineEventTypes.DELETE_CONFIRM_EVT;
};

export type CancelEvent = {
  type: DefinitionMachineEventTypes.CANCEL_EVENT_EVT;
};

export type DeleteRequestEvent = {
  type: DefinitionMachineEventTypes.DELETE_EVT;
};

export type ConfirmLastForkTaskRemoval = {
  type: DefinitionMachineEventTypes.CONFIRM_LAST_FORK_REMOVAL;
};

export type CollapseSidebarAndRightPanel = {
  type: DefinitionMachineEventTypes.COLLAPSE_SIDEBAR_AND_RIGHT_PANEL;
};

export type MoveTaskEvent = {
  type: DefinitionMachineEventTypes.MOVE_TASK_EVT;
  sourceTask: TaskDef;
  sourceTaskCrumbs: Crumb[];
  targetLocationCrumbs: Crumb[];
  targetTask: TaskDef;
  position: "ABOVE" | "BELOW" | "ADD_TASK_IN_DO_WHILE";
};

export type DoneInvokeLocalStorageMachineEvent = {
  type: "done.invoke.localCopyMachine";
  data: { workflow?: Partial<WorkflowDef>; isLocalStorageEmpty: boolean };
};

export type HandleLeftPanelExpandedEvent = {
  type: DefinitionMachineEventTypes.HANDLE_LEFT_PANEL_EXPANDED;
  onSelectNode: boolean;
};

export type MessageResetEvent = {
  type: DefinitionMachineEventTypes.MESSAGE_RESET_EVT;
};

export type RedirectToExecutionPageEvent = {
  type: DefinitionMachineEventTypes.REDIRECT_TO_EXECUTION_PAGE;
  executionId: string;
};

export type ExecuteWfEvent = {
  type: DefinitionMachineEventTypes.EXECUTE_WF;
};

export type NextStepImportSuccessfulDialogEvent = {
  type: DefinitionMachineEventTypes.NEXT_STEP_IMPORT_SUCCESSFUL_DIALOG;
};

export type DismissImportSuccessfulDialogEvent = {
  type: DefinitionMachineEventTypes.DISMISS_IMPORT_SUCCESSFUL_DIALOG;
};

export type SyncRunContextAndChangeTabEvent = {
  type: DefinitionMachineEventTypes.SYNC_RUN_CONTEXT_AND_CHANGE_TAB;
  data: {
    originalEvent: ChangeTabEvent;
    runMachineContext: RunWorkflowFields;
  };
};

export type WorkflowFromAgentEvent = {
  type: DefinitionMachineEventTypes.WORKFLOW_FROM_AGENT;
  workflow: Partial<WorkflowDef>;
};

export type ToggleAgentExpandedEvent = {
  type: DefinitionMachineEventTypes.TOGGLE_AGENT_EXPANDED;
  expanded?: boolean; // Optional: if provided, sets to that value; otherwise toggles
};

export type WorkflowDefinitionEvents =
  | ConfirmLastForkTaskRemoval
  | UpdateAttributesEvent
  | ErrorOnInvokeEvent
  | ChangeTabEvent
  | PerformOperationEvent
  | ReplaceTaskEvent
  | RemoveTaskEvent
  | AddNewSwitchTaskEvent
  | RemoveBranchFromTaskEvent
  | UpdateWorkflowMetadataEvent
  | WorkflowWithNoErrorsEvent
  | DownloadFileEvent
  | SavedSuccessfulEvent
  | SavedCancelledEvent
  | SaveRequestEvent
  | SaveAndCreateNewRequestEvent
  | SaveAsNewVersionRequestEvent
  | ResetRequestEvent
  | DeleteRequestEvent
  | ResetConfirmEvent
  | DeleteConfirmEvent
  | CancelEvent
  | UseLocalCopyChangesEvent
  | DoneInvokeLocalStorageMachineEvent
  | MoveTaskEvent
  | ChangeVersionEvent
  | HandleLeftPanelExpandedEvent
  | MessageResetEvent
  | HandleSaveAndRunEvent
  | HandleSaveAndCreateNewEvent
  | SaveAndRunRequestEvent
  | RedirectToExecutionPageEvent
  | ExecuteWfEvent
  | NextStepImportSuccessfulDialogEvent
  | DismissImportSuccessfulDialogEvent
  | SyncRunContextAndChangeTabEvent
  | CollapseSidebarAndRightPanel
  | WorkflowFromAgentEvent
  | ToggleAgentExpandedEvent;
export interface DefinitionMachineContext {
  currentWf?: Partial<WorkflowDef>;
  workflowChanges?: Partial<WorkflowDef>;
  currentUserInfo?: User;
  isNewWorkflow: boolean;
  workflowName?: string;
  currentVersion?: string;
  workflowVersions: number[];
  selectedTaskCrumbs: Crumb[];
  authHeaders: AuthHeaders; // This should be in auth actor
  message: ImportantMessage;
  openedTab: LeftPaneTabs;
  previousTab: LeftPaneTabs;
  lastPerformedOperation?: PerformedOperation;
  errorInspectorMachine?: ActorRef<ErrorInspectorMachineEvents>;
  downloadFileObj?: {
    data: Partial<WorkflowDef>;
    fileName: string;
    type: `application/json`;
  };
  lastRemovalOperation?: RemovalOperationPayload;
  flowMachine?: ActorRef<FlowEvents>;
  workflowTemplateId?: string;
  localCopyMessage?: string;
  collapseWorkflowList?: string[];
  codeTextReference?: CodeTextReference;
  isNewVersion?: boolean;
  secrets?: Record<string, unknown>[];
  envs?: Record<string, unknown>;
  initialSelectedTaskReferenceName?: string; // This is to dispatch to the flow machine
  workflowDefaultRunParam?: Record<string, unknown>;
  saveSourceEventType?: DefinitionMachineEventTypes; // This is to save the event source in context
  successfullyImportedWorkflowId?: string;
  importSummary?: ImportSummary;
  runTabFormState?: RunWorkflowFields;
  isAgentExpanded?: boolean;
}
