import { AlertProps } from "@mui/material/Alert";
import { CSSProperties } from "react";
interface MuiAlertProps extends AlertProps {
    style?: CSSProperties;
}
declare const MuiAlert: import("react").ForwardRefExoticComponent<Omit<MuiAlertProps, "ref"> & import("react").RefAttributes<HTMLDivElement>>;
export default MuiAlert;
export type { MuiAlertProps };
