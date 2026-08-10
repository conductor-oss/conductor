import { Close } from "@mui/icons-material";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import ActionButton from "components/ui/buttons/ActionButton";
import Button from "components/ui/buttons/MuiButton";
import MuiTypography from "components/ui/MuiTypography";
import SaveIcon from "components/icons/SaveIcon";
import XCloseIcon from "components/icons/XCloseIcon";
import { ReactNode } from "react";
import { modalStyles } from "./commonStyles";

type ConfirmModalProps = {
  title: string;
  titleIcon: ReactNode;
  progressLoading: boolean;
  handleSave: (data: any) => void;
  handleClose: () => void;
  content: ReactNode;
  disableSaveBtn?: boolean;
  disableBackdropClick?: boolean;
  disableCancelBtn?: boolean;
  id?: string;
  /** Wider paper for dense forms (default matches NEW GROUP-style 420px). */
  wide?: boolean;
  saveLabel?: string;
};

const ConfirmModal = ({
  title,
  titleIcon,
  progressLoading,
  handleSave,
  content,
  handleClose,
  disableSaveBtn,
  disableBackdropClick,
  disableCancelBtn,
  id = "confirm-modal-wrapper",
  wide = false,
  saveLabel = "Save",
}: ConfirmModalProps) => {
  const onClose = (
    event: Event,
    reason: "backdropClick" | "escapeKeyDown" | "closeButtonClick",
  ) => {
    if (reason === "backdropClick" && disableBackdropClick) {
      return false;
    }

    handleClose();
  };

  return (
    <Dialog
      PaperProps={{
        id,
      }}
      maxWidth={wide ? "lg" : "xs"}
      fullWidth={wide}
      open
      onClose={onClose}
      sx={[
        modalStyles.dialog,
        ...(wide
          ? [
              {
                "& .MuiPaper-root": {
                  // Avoid vw — it ignores scrollbar width and overflows the viewport.
                  width: "100%",
                  maxWidth: "920px",
                  boxSizing: "border-box",
                },
              },
            ]
          : []),
      ]}
    >
      <DialogTitle sx={modalStyles.title}>
        {titleIcon ? <MuiTypography>{titleIcon}</MuiTypography> : null}
        <MuiTypography lineHeight={1.3} fontSize={16} fontWeight="500" mr={2}>
          {title}
        </MuiTypography>
        <Close sx={modalStyles.closeIcon} onClick={handleClose} />
      </DialogTitle>
      <DialogContent
        sx={
          wide
            ? { minWidth: 0, maxWidth: "100%", overflowX: "hidden" }
            : undefined
        }
      >
        {content}
      </DialogContent>
      <DialogActions sx={{ background: "transparent", borderTop: "none" }}>
        <Button
          id="confirm-cancel-btn"
          color="secondary"
          onClick={handleClose}
          startIcon={<XCloseIcon />}
          disabled={disableCancelBtn}
        >
          Cancel
        </Button>
        <ActionButton
          id="confirm-save-btn"
          color="primary"
          progress={progressLoading}
          startIcon={<SaveIcon />}
          onClick={handleSave}
          disabled={disableSaveBtn}
        >
          {saveLabel}
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
};

export type { ConfirmModalProps };
export default ConfirmModal;
