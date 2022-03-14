import React from "react";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@material-ui/core";
import Text from "./Text";
import Button from "./Button";

export default function ({
  header = "Confirmation",
  message = "Please confirm",
  handleConfirmationValue,
  open,
}) {
  return (
    <Dialog
      fullWidth
      maxWidth="sm"
      open={open}
      onClose={() => handleConfirmationValue(false)}
    >
      <DialogTitle>{header}</DialogTitle>
      <DialogContent>
        <Text>{message}</Text>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => handleConfirmationValue(true)}>Confirm</Button>
        <Button
          variant="secondary"
          onClick={() => handleConfirmationValue(false)}
        >
          Cancel
        </Button>
      </DialogActions>
    </Dialog>
  );
}
