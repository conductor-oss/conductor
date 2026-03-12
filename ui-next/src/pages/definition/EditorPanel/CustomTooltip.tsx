import React from "react";
import { createPortal } from "react-dom";
import {
  Popper,
  Paper,
  ClickAwayListener,
  PopperPlacementType,
} from "@mui/material";
import { styled } from "@mui/material/styles";
import MuiIconButton from "components/MuiIconButton";
import XCloseIcon from "components/v1/icons/XCloseIcon";

interface CustomTooltipProps {
  open: boolean;
  anchorEl: HTMLElement | null;
  onClose: () => void;
  content: React.ReactNode;
  placement?: PopperPlacementType;
  maxWidth?: number;
}

const StyledPaper = styled(Paper)(({ theme }) => ({
  padding: theme.spacing(2),
  backgroundColor: "#F4F9FE",
  borderRadius: theme.spacing(1),
  boxShadow: "0px 2px 8px rgba(0, 0, 0, 0.15)",
  border: "1px solid #0D94DB",
  position: "relative",
  marginTop: theme.spacing(6),
  "&::before": {
    content: '""',
    position: "absolute",
    top: -5,
    left: 20,
    width: 10,
    height: 10,
    background: "white",
    border: "1px solid #0D94DB",
    backgroundColor: "#F4F9FE",
    transform: "rotate(45deg)",
    borderRight: "none",
    borderBottom: "none",
  },
}));

const CustomTooltip: React.FC<CustomTooltipProps> = ({
  open,
  anchorEl,
  onClose,
  content,
  placement = "bottom-start",
  maxWidth = 400,
}) => {
  return (
    <>
      {open &&
        createPortal(
          <div
            style={{
              position: "fixed",
              top: 0,
              left: 0,
              width: "100vw",
              height: "100vh",
              background: "rgba(0,0,0,0.3)",
              zIndex: 1200,
            }}
          />,
          document.body,
        )}
      <Popper
        open={open}
        anchorEl={anchorEl}
        placement={placement}
        style={{ zIndex: 1300 }}
      >
        <ClickAwayListener onClickAway={onClose}>
          <StyledPaper elevation={3} sx={{ maxWidth }}>
            <MuiIconButton
              aria-label="close"
              onClick={onClose}
              size="small"
              sx={{
                position: "absolute",
                top: 6,
                right: 6,
                zIndex: 2,
                color: "#0D94DB",
                background: "#F4F9FE",
                "&:hover": { background: "#e3f2fd" },
                padding: 0.5,
              }}
            >
              <XCloseIcon size={18} />
            </MuiIconButton>
            {content}
          </StyledPaper>
        </ClickAwayListener>
      </Popper>
    </>
  );
};

export default CustomTooltip;
