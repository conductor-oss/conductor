import { DialogProps } from "@mui/material";
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
declare const UnsavedChangesDialog: ({ handleAction, handleCancel, handleDiscard, actionButtonLabel, header, message, hasErrors, ...dialogProps }: UnsavedChangesDialogProps) => import("react").JSX.Element;
export default UnsavedChangesDialog;
