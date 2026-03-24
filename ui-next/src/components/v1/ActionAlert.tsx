import AlertTitle from "@mui/material/AlertTitle";
import Alert from "@mui/material/Alert";
import UndoIcon from "@mui/icons-material/Undo";
import { greyText, lightPurple, purple } from "theme/tokens/colors";
import MuiTypography from "../MuiTypography";

const actionAlertStyle = {
  alert: {
    background: "white",
    boxShadow: "4px 4px 10px 0px rgba(89, 89, 89, 0.41)",
    borderRadius: 1.5,
    color: "black",
    width: "fit-content",
    minWidth: "320px",
    "& button": {
      border: "1px solid",
      padding: 0.5,
      color: "gray",
    },
    "& .MuiSvgIcon-root": {
      fontSize: "14px",
    },
  },
  icon: {
    color: lightPurple,
  },
  title: {
    fontWeight: "600",
    fontSize: "14px",
  },
  message: {
    color: greyText,
  },
};

type ActionAlertProps = {
  title: string;
  message: string;
  onConfirm: () => void;
  onClose: () => void;
};

const ActionAlert = ({
  title,
  message,
  onConfirm,
  onClose,
}: ActionAlertProps) => {
  return (
    <Alert
      onClose={onClose}
      icon={<UndoIcon sx={actionAlertStyle.icon} />}
      sx={actionAlertStyle.alert}
    >
      <AlertTitle sx={actionAlertStyle.title}>{title}</AlertTitle>
      <MuiTypography sx={actionAlertStyle.message} onClick={onConfirm}>
        {message || (
          <>
            Want to remove that last action? Just{" "}
            <MuiTypography
              color={purple}
              component="span"
              fontWeight="500"
              cursor="pointer"
            >
              confirm to undo
            </MuiTypography>{" "}
            here.
          </>
        )}
      </MuiTypography>
    </Alert>
  );
};

export type { ActionAlertProps };
export default ActionAlert;
