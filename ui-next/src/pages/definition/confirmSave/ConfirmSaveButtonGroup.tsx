import Stack from "@mui/material/Stack";
import { useActor, useSelector } from "@xstate/react";
import { FunctionComponent } from "react";
import { ActorRef } from "xstate";

import CircularProgress from "@mui/material/CircularProgress";
import { ButtonTooltip } from "components/ui/buttons/ButtonTooltip";
import SaveIcon from "components/icons/SaveIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import { SaveWorkflowEvents, SaveWorkflowMachineEventTypes } from "./state";
import { useHotkeys } from "react-hotkeys-hook";
import { Key } from "ts-key-enum";
import { HOT_KEYS_WORKFLOW_DEFINITION } from "utils/constants/common";

interface ConfirmSaveButtonGroupProps {
  saveChangesActor: ActorRef<SaveWorkflowEvents>;
}

export const ConfirmSaveButtonGroup: FunctionComponent<
  ConfirmSaveButtonGroupProps
> = ({ saveChangesActor }) => {
  const [, send] = useActor(saveChangesActor);

  const handleConfirmSaveRequest = () => {
    send({ type: SaveWorkflowMachineEventTypes.CONFIRM_SAVE_EVT });
  };
  const handleCancelRequest = () => {
    send({ type: SaveWorkflowMachineEventTypes.CANCEL_SAVE_EVT });
  };
  const isSaving = useSelector(
    saveChangesActor,
    (state) =>
      state.matches("createWorkflow") ||
      state.matches("updateWorkflow") ||
      state.matches("refetchWorkflowDefinitions"),
  );

  // Hotkeys to confirm saving workflow
  useHotkeys(
    Key.Enter,
    (keyboardEvent) => {
      keyboardEvent.preventDefault();
      handleConfirmSaveRequest();
    },
    {
      scopes: HOT_KEYS_WORKFLOW_DEFINITION,
      enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
    },
  );

  // Hotkeys to cancel saving workflow
  useHotkeys(
    Key.Escape,
    (keyboardEvent) => {
      keyboardEvent.preventDefault();
      handleCancelRequest();
    },
    {
      scopes: HOT_KEYS_WORKFLOW_DEFINITION,
      enableOnFormTags: ["INPUT", "TEXTAREA", "SELECT"],
    },
  );

  return (
    <Stack flexDirection="row" gap={1} flexWrap="wrap">
      <ButtonTooltip
        id="confirm-cancel-btn"
        color="secondary"
        tooltip="Cancel return to editor (ESC)"
        onClick={handleCancelRequest}
        disabled={isSaving}
        startIcon={<XCloseIcon />}
      >
        Cancel
      </ButtonTooltip>

      <ButtonTooltip
        id="confirm-saving-btn"
        tooltip="Confirm Saving (↵)"
        onClick={handleConfirmSaveRequest}
        disabled={isSaving}
        startIcon={<SaveIcon />}
      >
        {isSaving ? (
          <>
            Saving
            <CircularProgress
              sx={{ ml: "10px" }}
              size="1.1rem"
              color="inherit"
            />
          </>
        ) : (
          "Confirm"
        )}
      </ButtonTooltip>
    </Stack>
  );
};
