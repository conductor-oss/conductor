import { EditorProps, OnMount } from "@monaco-editor/react";
import { BoxProps } from "@mui/material";
import { Theme } from "@mui/material/styles";
import { SxProps } from "@mui/system";
import { ConductorTooltipProps } from "components/ui/ConductorTooltip";
import { ReactNode } from "react";
export interface CodeBlockInputWrapperHandle {
    handleCopyValue: () => boolean;
}
interface CodeBlockInputWrapperProps {
    containerProps?: BoxProps;
    containerStyles?: SxProps<Theme>;
    label?: ReactNode;
    language?: string;
    languageLabel?: string;
    error?: boolean;
    value?: string;
    minHeight: number;
    disabled?: boolean;
    required?: boolean;
    tooltip?: Omit<ConductorTooltipProps, "children">;
    enableCopy?: boolean;
    onChange?: (value: string) => void;
    onMount?: OnMount;
    autoformat: boolean;
    autoFocus: boolean;
    options?: EditorProps["options"];
    editorProps?: Partial<EditorProps>;
    helperText?: string;
    onExpand?: () => void;
    isExpanded?: boolean;
    showLangLabel: boolean;
}
export declare const CodeBlockInputWrapper: ({ containerProps, containerStyles, label, language, languageLabel, error, value, minHeight, disabled, required, tooltip, enableCopy, onChange, onMount, autoformat, autoFocus, options, editorProps, helperText, onExpand, isExpanded, showLangLabel, }: CodeBlockInputWrapperProps) => import("react").JSX.Element;
export {};
