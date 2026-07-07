import { FunctionComponent } from "react";
import { Button, Stack, Tooltip, useMediaQuery } from "@mui/material";
import SaveIcon from "components/icons/SaveIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import ResetIcon from "components/icons/ResetIcon";
import CopyIcon from "components/icons/CopyIcon";
import { Theme } from "@mui/material/styles";
import { useAuth } from "components/features/auth";

export interface ScheduleButtonsProps {
  isConfirmingSave: boolean;
  couldNotParseJson: boolean;
  cancelConfirmSave: () => void;
  saveScheduleSubmit: () => void;
  clearScheduleForm: () => void;
  setSaveConfirmationOpen: () => void;
  canSave?: boolean;
  showClone?: boolean;
  canClone?: boolean;
  onCloneClick?: () => void;
}

const VALID_WIDTH_BREAKPOINT = 491;

const ScheduleButtons: FunctionComponent<ScheduleButtonsProps> = ({
  isConfirmingSave,
  couldNotParseJson,
  cancelConfirmSave,
  saveScheduleSubmit,
  clearScheduleForm,
  setSaveConfirmationOpen,
  canSave = true,
  showClone = false,
  canClone = false,
  onCloneClick,
}) => {
  const { isTrialExpired } = useAuth();
  const isValidWidth = useMediaQuery((theme: Theme) =>
    theme.breakpoints.down(VALID_WIDTH_BREAKPOINT),
  );

  return (
    <Stack display="flex" gap={2} flexWrap="wrap" width={["100%", "auto"]}>
      {isConfirmingSave ? (
        <Stack
          flexDirection={isValidWidth ? "column-reverse" : "row"}
          gap={3}
          flexWrap="wrap"
        >
          <Button
            variant="text"
            onClick={() => cancelConfirmSave()}
            startIcon={<XCloseIcon />}
          >
            Cancel
          </Button>
          <Button
            onClick={() => saveScheduleSubmit()}
            startIcon={<SaveIcon />}
            disabled={!canSave}
          >
            Confirm
          </Button>
        </Stack>
      ) : (
        <Stack
          flexDirection={isValidWidth ? "column-reverse" : "row"}
          gap={3}
          flexWrap="wrap"
        >
          <Button
            variant="text"
            onClick={() => clearScheduleForm()}
            disabled={couldNotParseJson || !canSave}
            startIcon={<ResetIcon />}
          >
            Reset
          </Button>
          {showClone && canClone && (
            <Tooltip title="Clone schedule — creates a new schedule with a new name">
              <span>
                <Button
                  id="clone-schedule-btn"
                  variant="outlined"
                  onClick={() => onCloneClick?.()}
                  disabled={isTrialExpired}
                  startIcon={<CopyIcon />}
                >
                  Clone
                </Button>
              </span>
            </Tooltip>
          )}
          <Button
            onClick={() => setSaveConfirmationOpen()}
            disabled={couldNotParseJson || isTrialExpired || !canSave}
            startIcon={<SaveIcon />}
          >
            Save
          </Button>
        </Stack>
      )}
    </Stack>
  );
};
export default ScheduleButtons;
