import React from "react";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@material-ui/core";
import { Text, Button } from "../../components";

export default function ResetConfirmationDialog({
  onClose,
  onConfirm,
  version,
}) {
  return (
    <Dialog fullWidth maxWidth="sm" open={version !== false} onClose={onClose}>
      <DialogTitle>Confirmation</DialogTitle>
      <DialogContent>
        <Text>
          You will lose all changes made in the editor. Are you sure to proceed?
        </Text>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onConfirm(version)}>Confirm</Button>
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
      </DialogActions>
    </Dialog>
  );
}
