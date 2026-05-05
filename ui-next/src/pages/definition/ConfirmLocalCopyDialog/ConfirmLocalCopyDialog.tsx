import { useCallback, FunctionComponent, useMemo } from "react";
import { useSelector, useActor } from "@xstate/react";
import { ActorRef } from "xstate";
import ConfirmChoiceDialog from "components/ui/dialogs/ConfirmChoiceDialog";
import { LocalCopyMachineEvents, LocalCopyMachineEventTypes } from "./state";
import {
  getLocalCopyTime,
  extractKeyFromContext,
} from "pages/runWorkflow/runWorkflowUtils";

interface ConfirmLocalCopyDialogProps {
  localCopyActor: ActorRef<LocalCopyMachineEvents>;
}

export const ConfirmLocalCopyDialog: FunctionComponent<
  ConfirmLocalCopyDialogProps
> = ({ localCopyActor }) => {
  const [, send] = useActor(localCopyActor);
  const isPromptUseLocalCopy = useSelector(localCopyActor, (state) =>
    state.matches("promptUseLocalCopy"),
  );

  const isNewWorkflow = useSelector(
    localCopyActor,
    (state) => state.context.isNewWorkflow,
  );
  const { workflowName, currentVersion } = useSelector(
    localCopyActor,
    (state) => state.context,
  );

  const maybeLocalCopyUpdateTime = getLocalCopyTime(
    extractKeyFromContext({ workflowName, currentVersion }),
  );
  const localCopySaveTime = useMemo(
    () =>
      isPromptUseLocalCopy &&
      isNewWorkflow === false &&
      maybeLocalCopyUpdateTime != null
        ? ` (Last saved on : ${maybeLocalCopyUpdateTime})`
        : "",
    [isPromptUseLocalCopy, isNewWorkflow, maybeLocalCopyUpdateTime],
  );

  const handleConfirmUseLocalChanges = useCallback(
    (val: boolean) =>
      send({
        type: val
          ? LocalCopyMachineEventTypes.USE_LOCAL_CHANGES_EVT
          : LocalCopyMachineEventTypes.CANCEL_EVENT_EVT,
      } as LocalCopyMachineEvents),
    [send],
  );
  return isPromptUseLocalCopy ? (
    <ConfirmChoiceDialog
      handleConfirmationValue={handleConfirmUseLocalChanges}
      message={`There are local changes for this workflow version. Do you want to load the local version${localCopySaveTime}?`}
      header="Confirmation"
      disableBackdropClick
      disableEscapeKeyDown
      confirmBtnLabel="Yes"
      cancelBtnLabel="No (discard local changes)"
    />
  ) : null;
};
