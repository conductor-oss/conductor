import { WorkflowDef, WorkflowMetadataI } from "types/WorkflowDef";
import { ActorRef } from "xstate";
import { EditInPlaceMachineEvents } from "../EditInPlaceWrapper/state/types";
import { UseLocalCopyChangesEvent } from "../../ConfirmLocalCopyDialog/state/types";
import { AuthHeaders } from "types/common";

export interface AccessKey {
  id: string;
  secret: string;
}

export enum WorkflowMetadataMachineEventTypes {
  UPDATE_METADATA = "UPDATE_METADATA",
  WORKFLOW_CHANGED = "WORKFLOW_CHANGED",
  DISABLE_EDITING = "DISABLE_EDITING",
  ENABLE_EDITING = "ENABLE_EDITING",
  FORCE_WORKFLOW = "FORCE_WORKFLOW",
  CREATE_APPLICATION = "CREATE_APPLICATION",
  CLOSE_KEYS_DIALOG = "CLOSE_KEYS_DIALOG",
}

export interface WorkflowMetadataMachineContext {
  metadataChanges: Partial<WorkflowMetadataI>;
  editableFields: string[];
  editableFieldActors: ActorRef<EditInPlaceMachineEvents>[];
  childActorsMachineName: "editInPlaceMachine" | "metadataFieldMachine";
  authHeaders?: AuthHeaders;
  applicationAccessKey?: AccessKey;
}

export type UpdateMetaDataEvent = {
  type: WorkflowMetadataMachineEventTypes.UPDATE_METADATA;
  metadataChanges: Partial<WorkflowMetadataI>;
};

export type WorkflowChangedEvent = {
  type: WorkflowMetadataMachineEventTypes.WORKFLOW_CHANGED;
  workflow: Partial<WorkflowDef>;
};

export type ForceWorkflowEvent = {
  type: WorkflowMetadataMachineEventTypes.FORCE_WORKFLOW;
  workflow: Partial<WorkflowDef>;
};

export type DisableEditingEvent = {
  type: WorkflowMetadataMachineEventTypes.DISABLE_EDITING;
};

export type EnableEditingEvent = {
  type: WorkflowMetadataMachineEventTypes.ENABLE_EDITING;
};

export type CreateApplicationEvent = {
  type: WorkflowMetadataMachineEventTypes.CREATE_APPLICATION;
};

export type CloseKeysDialogEvent = {
  type: WorkflowMetadataMachineEventTypes.CLOSE_KEYS_DIALOG;
};
export type WorkflowMetadataEvents =
  | UpdateMetaDataEvent
  | WorkflowChangedEvent
  | EnableEditingEvent
  | ForceWorkflowEvent
  | DisableEditingEvent
  | UseLocalCopyChangesEvent
  | CreateApplicationEvent
  | CloseKeysDialogEvent;
