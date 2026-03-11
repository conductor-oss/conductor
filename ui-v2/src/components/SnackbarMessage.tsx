import { Snackbar, SnackbarOrigin, SxProps } from "@mui/material";
import MuiAlert from "components/MuiAlert";
import { WarningCircle } from "@phosphor-icons/react";
import { ReactNode } from "react";
// How good is it to use lab components? https://material-ui.com/components/about-the-lab/

const useStyles = {
  customErrorColor: {
    background: "#fdeded",
    color: "#622524",
  },
  customWarningColor: {
    backgroundColor: "#FBA404",
  },
};

export const SnackbarMessage = ({
  message,
  onDismiss,
  severity = "info",
  sx = {},
  anchorOrigin = { vertical: "top", horizontal: "center" },
  autoHideDuration = 3000,
  id,
  action,
}: {
  message: string;
  onDismiss?: () => void;
  severity: "success" | "info" | "warning" | "error";
  sx?: SxProps;
  anchorOrigin?: SnackbarOrigin;
  autoHideDuration?: number;
  id?: string;
  action?: ReactNode;
}) => {
  const open = !!message;

  return (
    <Snackbar
      anchorOrigin={anchorOrigin}
      onClose={() => onDismiss && onDismiss()}
      open={open}
      autoHideDuration={autoHideDuration}
      sx={sx}
    >
      <MuiAlert
        icon={severity === "error" ? <WarningCircle color="red" /> : ""}
        variant="filled"
        elevation={6}
        onClose={() => onDismiss && onDismiss()}
        severity={severity}
        sx={severity === "error" ? useStyles.customErrorColor : undefined}
        id={id}
        style={
          severity === "warning" ? useStyles.customWarningColor : undefined
        }
        action={action}
      >
        {message}
      </MuiAlert>
    </Snackbar>
  );
};
