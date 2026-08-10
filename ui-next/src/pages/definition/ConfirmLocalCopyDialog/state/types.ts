import { WorkflowDef } from "types/WorkflowDef";
import { DoneInvokeEvent } from "xstate";
import { WorkflowWithNoErrorsEvent } from "../../errorInspector/state";

export enum LocalCopyMachineEventTypes {
  USE_LOCAL_CHANGES_EVT = "USE_LOCAL_CHANGES_EVT",
  CANCEL_EVENT_EVT = "CANCEL_EVENT_EVT",
  USE_LOCAL_COPY_WORKFLOW = "USE_LOCAL_COPY_WORKFLOW",
  REMOVE_LOCAL_COPY = "REMOVE_LOCAL_COPY",
  REMOVE_LOCAL_COPY_MESSAGE = "REMOVE_LOCAL_COPY_MESSAGE",
  UPDATE_ATTRIBS_EVT = "updateAttributes",
}

export interface LocalCopyMachineContext {
  lastStoredVersion?: Partial<WorkflowDef>;
  workflowName: string;
  currentVersion?: number;
  isNewWorkflow: boolean;
  currentWf: Partial<WorkflowDef>;
}

export type UseLocalChangesEvent = {
  type: LocalCopyMachineEventTypes.USE_LOCAL_CHANGES_EVT;
};

export type CancelEvent = {
  type: LocalCopyMachineEventTypes.CANCEL_EVENT_EVT;
};

export type RemoveLocalCopyEvent = {
  type: LocalCopyMachineEventTypes.REMOVE_LOCAL_COPY;
};

export type UseLocalCopyChangesEvent = {
  type: LocalCopyMachineEventTypes.USE_LOCAL_COPY_WORKFLOW;
  workflow: Partial<WorkflowDef>;
};

export type RemoveLocalCopyMessageEvent = {
  type: LocalCopyMachineEventTypes.REMOVE_LOCAL_COPY_MESSAGE;
};

export type UpdateAttribsEvent = {
  type: LocalCopyMachineEventTypes.UPDATE_ATTRIBS_EVT;
};

export type LocalCopyMachineEvents =
  | UpdateAttribsEvent
  | UseLocalChangesEvent
  | WorkflowWithNoErrorsEvent
  | RemoveLocalCopyEvent
  | RemoveLocalCopyMessageEvent
  | CancelEvent
  | DoneInvokeEvent<string>;
