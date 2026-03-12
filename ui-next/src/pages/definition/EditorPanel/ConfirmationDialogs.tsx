import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import { ActorRef } from "xstate";
import { ConfirmDialog } from "../ConfirmDialog";
import { ConfirmLocalCopyDialog } from "../ConfirmLocalCopyDialog/ConfirmLocalCopyDialog";
import { ConfirmWorkflowOverride } from "../confirmSave";

interface ConfirmationDialogsProps {
  isConfirmReset: boolean;
  isConfirmDelete: boolean;
  isConfirmingForkRemoval: boolean;
  isSaveRequest: boolean;
  localCopyActor: ActorRef<any> | undefined;
  saveChangesActor: ActorRef<any> | undefined;
  onResetConfirmation: (val: boolean) => void;
  onDeleteConfirmation: (val: boolean) => void;
  onCancelRequest: () => void;
  onConfirmLastForkRemovalRequest: () => void;
}

export const ConfirmationDialogs = ({
  isConfirmReset,
  isConfirmDelete,
  isConfirmingForkRemoval,
  isSaveRequest,
  localCopyActor,
  saveChangesActor,
  onResetConfirmation,
  onDeleteConfirmation,
  onCancelRequest,
  onConfirmLastForkRemovalRequest,
}: ConfirmationDialogsProps) => {
  return (
    <>
      {isConfirmReset && (
        <ConfirmChoiceDialog
          handleConfirmationValue={onResetConfirmation}
          message={
            "You will lose all changes made in the editor. Please confirm resetting workflow to its original state."
          }
        />
      )}
      {isConfirmDelete && (
        <ConfirmChoiceDialog
          handleConfirmationValue={onDeleteConfirmation}
          message={
            "Are you sure you want to delete this version of the workflow definition? Change cannot be undone."
          }
        />
      )}
      {isConfirmingForkRemoval && (
        <ConfirmDialog
          shouldPrompt={isConfirmingForkRemoval}
          onCancel={onCancelRequest}
          onConfirm={onConfirmLastForkRemovalRequest}
          message="Removing the last fork will remove both fork and join. Are you sure ?"
        />
      )}
      {localCopyActor && (
        <ConfirmLocalCopyDialog localCopyActor={localCopyActor} />
      )}
      {isSaveRequest && saveChangesActor && (
        <ConfirmWorkflowOverride saveChangesActor={saveChangesActor} />
      )}
    </>
  );
};
