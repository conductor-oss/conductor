import { assign, sendParent } from "xstate";
import { cancel } from "xstate/lib/actions";
import { MetadataFieldMachineContext, ChangeValueEvent } from "./types";
import { WorkflowMetadataMachineEventTypes } from "pages/definition/WorkflowMetadata/state/types";

export const persistChanges = assign<
  MetadataFieldMachineContext,
  ChangeValueEvent
>({
  value: (_context, { value }) => value,
});

export const addSomeKey = assign<MetadataFieldMachineContext, any>({
  someKey: (_context) => Math.random().toString(36).substring(2, 7), // hack for json components. to re-render on external value change
});

export const debounceSyncWithParent = sendParent(
  (context: MetadataFieldMachineContext, { value }: ChangeValueEvent) => ({
    type: WorkflowMetadataMachineEventTypes.UPDATE_METADATA,
    metadataChanges: { [context.fieldName]: value },
  }),
  /* { delay: 500, id: "sync_val_with_parent" } */
);

export const cancelSyncWithParent = cancel("sync_val_with_parent");
