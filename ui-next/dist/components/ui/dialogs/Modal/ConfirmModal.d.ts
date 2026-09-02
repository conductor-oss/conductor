import { ReactNode } from "react";
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
    /** Wider paper for dense forms (default matches NEW GROUP-style 420px). */
    wide?: boolean;
    saveLabel?: string;
};
declare const ConfirmModal: ({ title, titleIcon, progressLoading, handleSave, content, handleClose, disableSaveBtn, disableBackdropClick, disableCancelBtn, id, wide, saveLabel, }: ConfirmModalProps) => import("react").JSX.Element;
export type { ConfirmModalProps };
export default ConfirmModal;
