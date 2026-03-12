import React from "react";
import { Alert, AlertTitle, styled } from "@mui/material";

const StyledAlert = styled(Alert)(() => ({
  backgroundColor: "#E8F5E9",
  color: "#1B5E20",
  "& .MuiAlert-icon": {
    color: "#1B5E20",
  },
  border: "1px solid #A5D6A7",
  borderRadius: "4px",
  padding: "6px 16px",
  "& .MuiAlert-message": {
    padding: "8px 0",
    fontSize: "14px",
    color: "rgba(37, 37, 37, 1)",
  },
  boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
  position: "fixed",
  top: "16px",
  right: "16px",
  zIndex: 1400,
  "& .MuiAlertTitle-root": {
    color: "black",
    fontWeight: 600,
    marginBottom: "2px",
  },
}));

interface FloatingMuiAlertProps {
  title?: string;
  message?: string;
  onClose?: () => void;
}

const FloatingMuiAlert: React.FC<FloatingMuiAlertProps> = ({
  title = "Congratulations! You've created a workflow!",
  message = "Edit whatever you want, or not, and take it for a Run!",
  onClose,
}) => {
  return (
    <StyledAlert severity="success" onClose={onClose}>
      <AlertTitle>{title}</AlertTitle>
      {message}
    </StyledAlert>
  );
};

export default FloatingMuiAlert;
