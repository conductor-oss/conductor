import CloseIcon from "@mui/icons-material/Close";
import WarningRoundedIcon from "@mui/icons-material/WarningRounded";
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogProps,
  DialogTitle,
  IconButton,
  Typography,
} from "@mui/material";
import { ReactNode } from "react";

interface UnsavedChangesDialogProps extends DialogProps {
  handleAction: () => void;
  handleCancel: () => void;
  handleDiscard: () => void;
  header?: ReactNode;
  message?: ReactNode;
  actionButtonLabel?: string;
  hasErrors?: boolean;
}

const UnsavedChangesDialog = ({
  handleAction,
  handleCancel,
  handleDiscard,
  actionButtonLabel = "Save",
  header = "Unsaved Changes",
  message = "You have unsaved changes. What would you like to do?",
  hasErrors = false,
  ...dialogProps
}: UnsavedChangesDialogProps) => {
  return (
    <Dialog
      onClose={handleCancel}
      sx={{
        "& .MuiDialog-paper": {
          backgroundColor: hasErrors ? "#FFF1F2" : undefined,
        },
      }}
      {...dialogProps}
    >
      <DialogTitle
        sx={{
          display: "flex",
          gap: 2,
          alignItems: "center",
          position: "relative",
          backgroundColor: "transparent",
          border: "none",
        }}
        color={hasErrors ? "error" : "inherit"}
      >
        {hasErrors && <WarningRoundedIcon />}
        {header}
        <IconButton
          aria-label="close"
          onClick={handleCancel}
          sx={{
            position: "absolute",
            right: 8,
            top: 8,
            color: (theme) => theme.palette.grey[500],
          }}
        >
          <CloseIcon />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Box mt={2}>
          <Typography variant="body2">{message}</Typography>
        </Box>
      </DialogContent>
      <DialogActions sx={{ backgroundColor: "transparent" }}>
        <Box
          display="flex"
          gap={3}
          justifyContent="space-between"
          width="100%"
          minWidth={{ sm: 400 }}
          flexDirection={{ xs: "column", sm: "row" }}
          alignItems={{ xs: "stretch", sm: "center" }}
        >
          <Button onClick={handleCancel} variant="outlined">
            Cancel
          </Button>
          <Box
            display="flex"
            gap={3}
            flexDirection={{ xs: "column", sm: "row" }}
          >
            <Button onClick={handleDiscard} color="error">
              Discard
            </Button>
            <Button onClick={handleAction}>{actionButtonLabel}</Button>
          </Box>
        </Box>
      </DialogActions>
    </Dialog>
  );
};

export default UnsavedChangesDialog;
