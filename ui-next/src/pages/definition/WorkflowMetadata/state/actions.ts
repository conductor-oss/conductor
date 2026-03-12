import { assign, sendParent, spawn, forwardTo, DoneInvokeEvent } from "xstate";
import { cancel, send, pure } from "xstate/lib/actions";
import {
  UpdateMetaDataEvent,
  WorkflowChangedEvent,
  WorkflowMetadataMachineContext,
} from "./types";
import _get from "lodash/get";
import { DefinitionMachineEventTypes } from "pages/definition/state/types";
import { WorkflowDef } from "types/WorkflowDef";
import { extractWorkflowMetadata } from "../../helpers";
import { EditInPlaceEventTypes } from "../EditInPlaceWrapper/state/types";
import { editInPlaceMachine } from "../EditInPlaceWrapper/state/machine";
import { metadataFieldMachine } from "pages/definition/EditorPanel/WorkflowPropertiesFormTab/state/machine";

export const persistPartialMetaDataChanges = assign<
  WorkflowMetadataMachineContext,
  UpdateMetaDataEvent
>({
  metadataChanges: (
    { metadataChanges },
    { metadataChanges: partialChanges },
  ) => ({ ...metadataChanges, ...partialChanges }),
});

export const syncWithParent = sendParent(
  (__, { metadataChanges }: UpdateMetaDataEvent) => ({
    type: DefinitionMachineEventTypes.UPDATE_WF_METADATA_EVT,
    workflowMetadata: metadataChanges,
  }),
);

export const cancelSyncWithParent = cancel("sync_with_parent");

export const updateLocalCopy = assign<
  WorkflowMetadataMachineContext,
  WorkflowChangedEvent
>((context, { workflow }) => {
  const metadata = extractWorkflowMetadata(workflow as Partial<WorkflowDef>);

  return {
    metadataChanges: metadata,
  };
});

// takes the machine name from context and spawns actors for each field
export const spawnFieldActors = assign<WorkflowMetadataMachineContext>({
  editableFieldActors: (context) => {
    const childMachines = {
      editInPlaceMachine: editInPlaceMachine,
      metadataFieldMachine: metadataFieldMachine,
    };
    const machineInstance = _get(childMachines, context.childActorsMachineName);
    return context.editableFields.map((field) =>
      spawn(
        machineInstance.withContext({
          value: _get(context.metadataChanges, field),
          fieldName: field,
        }),
        `${field}-field`,
      ),
    );
  },
});

// @ts-ignore
export const notifyActors = pure((context: WorkflowMetadataMachineContext) => {
  return context.editableFields.map((field) =>
    send(
      {
        type: EditInPlaceEventTypes.VALUE_UPDATED,
        value: _get(context.metadataChanges, field),
      },
      { to: `${field}-field` },
    ),
  );
});

export const forwardActionToActors = pure(
  // @ts-ignore
  (context: WorkflowMetadataMachineContext) => {
    return context.editableFields.map((field) => forwardTo(`${field}-field`));
  },
);

export const persistApplicationKeys = assign<
  WorkflowMetadataMachineContext,
  DoneInvokeEvent<{ id: string; secret: string }>
>((_context, { data }) => {
  return {
    applicationAccessKey: {
      id: data.id,
      secret: data.secret,
    },
  };
});
