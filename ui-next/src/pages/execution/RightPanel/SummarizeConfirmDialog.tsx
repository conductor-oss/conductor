import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import ActionButton from "components/ui/buttons/ActionButton";
import XCloseIcon from "components/icons/XCloseIcon";
import { Button, Text } from "components/index";

interface SummarizeConfirmDialogProps {
  open: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function SummarizeConfirmDialog({
  open,
  onCancel,
  onConfirm,
}: SummarizeConfirmDialogProps) {
  return (
    <Dialog
      fullWidth
      maxWidth="sm"
      open={open}
      onClose={onCancel}
      sx={{ "& .MuiDialog-paperWidthSm": { maxWidth: "480px" } }}
    >
      <DialogTitle>Show full iteration data?</DialogTitle>
      <DialogContent>
        <Box mt={5}>
          <Text
            sx={{ opacity: 0.8, fontSize: "15px", lineHeight: 1.5 }}
            component="div"
          >
            This will re-fetch the workflow without summarization. For workflows
            with many iterations or large output payloads, this may be slow. Use{" "}
            <strong>Summarize</strong> on the task to limit data size.
          </Text>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button variant="text" onClick={onCancel} startIcon={<XCloseIcon />}>
          Cancel
        </Button>
        <ActionButton color="primary" onClick={onConfirm}>
          Continue
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
}
