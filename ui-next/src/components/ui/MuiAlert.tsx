import Alert, { AlertProps } from "@mui/material/Alert";
import { CSSProperties, forwardRef } from "react";

interface MuiAlertProps extends AlertProps {
  style?: CSSProperties;
}

const MuiAlert = forwardRef<HTMLDivElement, MuiAlertProps>(
  ({ style, ...props }, ref) => {
    return <Alert ref={ref} {...props} sx={{ ...style }} />;
  },
);

export default MuiAlert;
export type { MuiAlertProps };
