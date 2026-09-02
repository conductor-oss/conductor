import { SnackbarOrigin, SxProps } from "@mui/material";
import { ReactNode } from "react";
export declare const SnackbarMessage: ({ message, onDismiss, severity, sx, anchorOrigin, autoHideDuration, id, action, }: {
    message: string;
    onDismiss?: () => void;
    severity: "success" | "info" | "warning" | "error";
    sx?: SxProps;
    anchorOrigin?: SnackbarOrigin;
    autoHideDuration?: number;
    id?: string;
    action?: ReactNode;
}) => import("react").JSX.Element;
