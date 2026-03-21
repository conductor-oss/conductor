import { Close } from "@mui/icons-material";
import {
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import ActionButton from "components/ActionButton";
import Button from "components/MuiButton";
import MuiTypography from "components/MuiTypography";
import SaveIcon from "components/v1/icons/SaveIcon";
import XCloseIcon from "components/v1/icons/XCloseIcon";
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
      maxWidth={"xs"}
      open
      onClose={onClose}
      sx={modalStyles.dialog}
    >
      <DialogTitle sx={modalStyles.title}>
        {titleIcon ? <MuiTypography>{titleIcon}</MuiTypography> : null}
        <MuiTypography lineHeight={1.3} fontSize={16} fontWeight="500" mr={2}>
          {title}
        </MuiTypography>
        <Close sx={modalStyles.closeIcon} onClick={handleClose} />
      </DialogTitle>
      <DialogContent>{content}</DialogContent>
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
          Save
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
};

export type { ConfirmModalProps };
export default ConfirmModal;
