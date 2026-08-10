import { assign, sendParent } from "xstate";
import { cancel } from "xstate/lib/actions";
import { EditInPlaceMachineContext, ChangeValueEvent } from "./types";
import { WorkflowMetadataMachineEventTypes } from "../../state/types";

export const persistChanges = assign<
  EditInPlaceMachineContext,
  ChangeValueEvent
>({
  value: (_context, { value }) => value,
});

export const debounceSyncWithParent = sendParent(
  (context: EditInPlaceMachineContext, { value }: ChangeValueEvent) => ({
    type: WorkflowMetadataMachineEventTypes.UPDATE_METADATA,
    metadataChanges: { [context.fieldName]: value },
  }),
  /* { delay: 500, id: "sync_with_parent" } // Use function to reduce debounce if code tab is opened. */
);

export const cancelSyncWithParent = cancel("sync_with_parent");
