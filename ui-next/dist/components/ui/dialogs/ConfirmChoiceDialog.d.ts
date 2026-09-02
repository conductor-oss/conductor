import { ReactNode } from "react";
export default function ConfirmChoiceDialog({ header, message, handleConfirmationValue, isInputConfirmation, valueToBeDeleted, cancelBtnLabel, confirmBtnLabel, disableBackdropClick, disableEscapeKeyDown, hideCancelBtn, id, isConfirmLoading, }: {
    header?: string;
    message?: string | ReactNode;
    handleConfirmationValue: (b: boolean) => void;
    valueToBeDeleted?: string;
    isInputConfirmation?: boolean;
    cancelBtnLabel?: string;
    confirmBtnLabel?: string;
    disableBackdropClick?: boolean;
    disableEscapeKeyDown?: boolean;
    hideCancelBtn?: boolean;
    id?: string;
    isConfirmLoading?: boolean;
}): import("react").JSX.Element;
