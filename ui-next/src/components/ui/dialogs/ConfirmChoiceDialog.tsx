/**
 * The product's confirmation dialog: a question, an optional type-the-name gate, and two
 * buttons.
 *
 * The theme paints DialogTitle and DialogActions grey. The newer dialogs
 * (UnsavedChangesDialog, ConfirmModal) each opted out of that per instance; this one does
 * the same, so every confirmation in the product reads the same way. Destructive
 * confirmations additionally get a trash badge and a red confirm button, because "Confirm"
 * with a save icon gave a delete the same weight as saving a form.
 */
import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import { ReactNode, useState } from "react";

import SaveIcon from "components/icons/SaveIcon";
import TrashIcon from "components/icons/TrashIcon";
import { Button, Text } from "components/index";
import ActionButton from "components/ui/buttons/ActionButton";
import ConductorInput from "components/ui/inputs/ConductorInput";

import { CONFIRM_FIELD_ID, confirmDialogStyle } from "./confirmDialogStyle";

export default function ConfirmChoiceDialog({
  header = "Confirmation",
  message = "Please confirm",
  handleConfirmationValue,
  isInputConfirmation,
  valueToBeDeleted,
  cancelBtnLabel,
  confirmBtnLabel,
  disableBackdropClick,
  disableEscapeKeyDown,
  hideCancelBtn,
  id = "confirm-choice-dialog",
  isConfirmLoading = false,
  destructive,
  inputLabel,
}: {
  header?: ReactNode;
  message?: string | ReactNode;
  handleConfirmationValue: (b: boolean) => void;
  valueToBeDeleted?: string;
  isInputConfirmation?: boolean;
  cancelBtnLabel?: string;
  confirmBtnLabel?: string;
  disableBackdropClick?: boolean;
  disableEscapeKeyDown?: boolean;
  hideCancelBtn?: boolean;
  id?: string;
  isConfirmLoading?: boolean;
  /**
   * Red confirm button and a trash badge. Defaults to `isInputConfirmation`, which is how
   * the button colour was already chosen, so existing callers keep the treatment they had.
   */
  destructive?: boolean;
  /** Sits directly above the typed-name field, 6px up, as its label. */
  inputLabel?: ReactNode;
}) {
  const [inputValue, setInputValue] = useState("");

  const isDestructive = destructive ?? !!isInputConfirmation;

  const onClose = (
    event: Event,
    reason: "backdropClick" | "escapeKeyDown" | "closeButtonClick",
  ) => {
    if (disableBackdropClick && reason === "backdropClick") {
      return false;
    }

    handleConfirmationValue(false);
  };

  return (
    <Dialog
      fullWidth
      maxWidth="sm"
      open
      onClose={onClose}
      sx={{ "& .MuiDialog-paperWidthSm": confirmDialogStyle.paper }}
      disableEscapeKeyDown={disableEscapeKeyDown}
      PaperProps={{ id }}
    >
      <DialogTitle sx={confirmDialogStyle.title}>
        {isDestructive && (
          <Box id="choice-dialog-icon" sx={confirmDialogStyle.badge}>
            <TrashIcon />
          </Box>
        )}
        {header}
      </DialogTitle>

      <DialogContent sx={confirmDialogStyle.content}>
        <Box sx={confirmDialogStyle.body}>
          <Text sx={confirmDialogStyle.message} component="div">
            {message}
          </Text>
          {isInputConfirmation && (
            <Box>
              {inputLabel && (
                <Box
                  component="label"
                  htmlFor={CONFIRM_FIELD_ID}
                  sx={confirmDialogStyle.inputLabel}
                >
                  {inputLabel}
                </Box>
              )}
              <ConductorInput
                id={CONFIRM_FIELD_ID}
                value={inputValue}
                onTextInputChange={(value) => setInputValue(value)}
                fullWidth
                color="secondary"
                autoFocus
              />
            </Box>
          )}
        </Box>
      </DialogContent>

      <DialogActions sx={confirmDialogStyle.actions}>
        {!hideCancelBtn && (
          <Button
            id="choice-dialog-cancel-btn"
            variant="outlined"
            sx={confirmDialogStyle.cancelButton}
            onClick={() => handleConfirmationValue(false)}
            disabled={isConfirmLoading}
          >
            {cancelBtnLabel ? cancelBtnLabel : "Cancel"}
          </Button>
        )}
        <ActionButton
          id="choice-dialog-confirm-btn"
          variant="contained"
          color={isDestructive ? "error" : "primary"}
          sx={isDestructive ? confirmDialogStyle.destructiveButton : undefined}
          onClick={() => handleConfirmationValue(true)}
          disabled={isInputConfirmation && inputValue !== valueToBeDeleted}
          startIcon={
            isDestructive ? (
              <TrashIcon />
            ) : confirmBtnLabel ? null : (
              <SaveIcon />
            )
          }
          progress={isConfirmLoading}
        >
          {confirmBtnLabel ? confirmBtnLabel : "Confirm"}
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
}
