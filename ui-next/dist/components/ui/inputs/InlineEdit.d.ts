import { ReactNode } from "react";
export declare const InlineEdit: ({ value, editLabel, saveLabel, cancelLabel, flexGrow, onSave, onChangeMode, error, helperText, notAllowedCharRegex, disabled, }: {
    value: string;
    editLabel?: ReactNode;
    saveLabel?: ReactNode;
    cancelLabel?: ReactNode;
    flexGrow?: number;
    onSave: (val: string) => void;
    onChangeMode?: (edit: boolean) => void;
    error?: boolean;
    helperText?: string;
    notAllowedCharRegex?: RegExp;
    disabled?: boolean;
}) => import("react").JSX.Element;
