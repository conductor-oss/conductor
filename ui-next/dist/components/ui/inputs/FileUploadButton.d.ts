import { MuiButtonProps } from "components/ui/buttons/MuiButton";
import { ElementType } from "react";
export interface FileUploadButtonProps extends MuiButtonProps {
    value?: string;
    onChangeFile: (fileName: string, fileValue: string) => void;
    onClearFile?: () => void;
    accept?: string;
    component?: ElementType;
    label?: string;
    helperText?: string;
    error?: boolean;
}
export default function FileUploadButton({ value, onChangeFile: handleChange, onClearFile, accept, label, helperText, error, ...props }: FileUploadButtonProps): import("react").JSX.Element;
