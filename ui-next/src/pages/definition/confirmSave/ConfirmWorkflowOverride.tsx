import { useCallback, FunctionComponent } from "react";
import { useSelector, useActor } from "@xstate/react";
import { ActorRef } from "xstate";
import ConfirmChoiceDialog from "components/ConfirmChoiceDialog";
import { SaveWorkflowEvents, SaveWorkflowMachineEventTypes } from "./state";
import { Typography } from "components/index";
import { tryToJson } from "utils/utils";
import { WorkflowDef } from "types/WorkflowDef";

interface ConfirmWorkflowOverrideProps {
  saveChangesActor: ActorRef<SaveWorkflowEvents>;
}

export const ConfirmWorkflowOverride: FunctionComponent<
  ConfirmWorkflowOverrideProps
> = ({ saveChangesActor }) => {
  const [, send] = useActor(saveChangesActor);
  const isPromptOverride = useSelector(saveChangesActor, (state) =>
    state.matches("confirmOverride"),
  );

  const editorChanges = useSelector(
    saveChangesActor,
    (state) => tryToJson(state.context.editorChanges) as WorkflowDef,
  );

  const handleConfirmOverride = useCallback(
    (val: boolean) =>
      send({
        type: val
          ? SaveWorkflowMachineEventTypes.CONFIRM_OVERRIDE_EVT
          : SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT,
      } as SaveWorkflowEvents),
    [send],
  );

  return isPromptOverride ? (
    <ConfirmChoiceDialog
      handleConfirmationValue={handleConfirmOverride}
      message={
        <>
          <Typography fontSize={14}>
            There seems to be workflow with same name and version.
          </Typography>
          <Typography fontSize={14}>
            Should we override&nbsp;
            <Typography
              fontSize={14}
              component="strong"
              color="red"
              fontWeight={500}
            >
              {editorChanges?.name}
            </Typography>
            &nbsp;? This cannot be undone.
          </Typography>

          <Typography fontSize={14} mt={2}>
            Please type&nbsp;
            <Typography fontSize={14} component="strong" fontWeight={500}>
              {editorChanges?.name}
            </Typography>
            &nbsp;to confirm.
          </Typography>
        </>
      }
      header="Override ?"
      isInputConfirmation
      valueToBeDeleted={editorChanges?.name}
    />
  ) : null;
};
