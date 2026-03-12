import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import ConductorInput from "components/v1/ConductorInput";
import SaveIcon from "components/v1/icons/SaveIcon";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import { ReactNode, useState } from "react";
import { Button, Text } from "components/index";
import ActionButton from "components/ActionButton";

const style = {
  confirmationMessage: {
    opacity: 0.8,
    paddingLeft: "10px",
    fontSize: "15px",
    lineHeight: 1.5,
    "& p": {
      fontSize: "15px",
      fontWeight: "normal",
    },
    "& svg": {
      fontSize: "15px",
    },
  },
};

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
}: {
  header?: string;
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
}) {
  const [inputValue, setInputValue] = useState("");

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
      sx={{
        "& .MuiDialog-paperWidthSm": {
          maxWidth: "690px",
        },
      }}
      disableEscapeKeyDown={disableEscapeKeyDown}
      PaperProps={{
        id,
      }}
    >
      <DialogTitle>{header}</DialogTitle>
      <DialogContent>
        <Box mt={4}>
          <Text
            sx={style.confirmationMessage}
            style={{ marginRight: 10 }}
            component="div"
          >
            {message}
          </Text>
          {isInputConfirmation && (
            <ConductorInput
              sx={{ mt: 2, pr: 5 }}
              id="choice-dialog-confirmation-field"
              value={inputValue}
              onTextInputChange={(value) => setInputValue(value)}
              fullWidth
              color="secondary"
              autoFocus
            />
          )}
        </Box>
      </DialogContent>
      <DialogActions>
        {!hideCancelBtn && (
          <Button
            id="choice-dialog-cancel-btn"
            variant="text"
            onClick={() => handleConfirmationValue(false)}
            startIcon={cancelBtnLabel ? null : <XCloseIcon />}
            disabled={isConfirmLoading}
          >
            {cancelBtnLabel ? cancelBtnLabel : "Cancel"}
          </Button>
        )}
        <ActionButton
          id="choice-dialog-confirm-btn"
          onClick={() => handleConfirmationValue(true)}
          disabled={isInputConfirmation && inputValue !== valueToBeDeleted}
          color={isInputConfirmation ? "error" : "primary"}
          startIcon={confirmBtnLabel ? null : <SaveIcon />}
          progress={isConfirmLoading}
        >
          {confirmBtnLabel ? confirmBtnLabel : "Confirm"}
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
}
