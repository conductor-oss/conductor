import { ActorRef, DoneInvokeEvent } from "xstate";
import { ErrorInspectorMachineEvents } from "../../errorInspector/state/types";
import { WorkflowDef } from "types/WorkflowDef";

export enum SaveWorkflowMachineEventTypes {
  CONFIRM_SAVE_EVT = "CONFIRM_SAVE",
  CANCEL_SAVE_EVT = "CANCEL_SAVE",
  EDIT_EVT = "EDIT_EVT",
  EDIT_DEBOUNCE_EVT = "CANCEL_DEBOUNCE",
  CONFIRM_OVERRIDE_EVT = "CONFIRM_OVERRIDE_EVT",

  SAVED_SUCCESSFUL = "SAVED_SUCCESSFUL",
  SAVED_CANCELLED = "SAVED_CANCELLED",
}

export type ConfirmSaveEvent = {
  type: SaveWorkflowMachineEventTypes.CONFIRM_SAVE_EVT;
};

export type CancelSaveEvent = {
  type: SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT;
};

export type EditEvent = {
  type: SaveWorkflowMachineEventTypes.EDIT_EVT;
  changes: string;
};

export type EditDebounceEvent = {
  type: SaveWorkflowMachineEventTypes.EDIT_DEBOUNCE_EVT;
  changes: string;
};

export type ConfirmOverrideEvent = {
  type: SaveWorkflowMachineEventTypes.CONFIRM_OVERRIDE_EVT;
};

export type SavedSuccessfulEvent = {
  type: SaveWorkflowMachineEventTypes.SAVED_SUCCESSFUL;
  workflow: Partial<WorkflowDef>;
  isNewWorkflow: boolean;
  workflowName: string;
  currentVersion: number;
};

export type SavedCancelledEvent = {
  type: SaveWorkflowMachineEventTypes.SAVED_CANCELLED;
  workflowChanges?: Partial<WorkflowDef>;
};

export type SaveWorkflowEvents =
  | ConfirmSaveEvent
  | CancelSaveEvent
  | EditEvent
  | EditDebounceEvent
  | DoneInvokeEvent<string>
  | ConfirmOverrideEvent;

export interface SaveWorkflowMachineContext {
  currentWf: Partial<WorkflowDef>;
  editorChanges: string;
  isNewWorkflow: boolean;
  workflowName: string;
  authHeaders: Record<string, string>;
  currentVersion: number;
  errorInspectorMachine?: ActorRef<ErrorInspectorMachineEvents>;
  isNewVersion?: boolean;
  isContinueCreate?: boolean;
}
