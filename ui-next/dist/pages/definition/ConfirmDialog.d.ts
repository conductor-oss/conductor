import { FunctionComponent } from "react";
interface ConfirmDialogProps {
    onConfirm: () => void;
    onCancel: () => void;
    shouldPrompt: boolean;
    message: string;
    title?: string;
}
export declare const ConfirmDialog: FunctionComponent<ConfirmDialogProps>;
export {};
