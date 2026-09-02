import { TextFieldProps, Theme, SxProps } from "@mui/material";
export type RoundedInputProps = Omit<TextFieldProps, "onBlur" | "onChange"> & {
    placeholder?: string;
    autoFocus?: boolean;
    required?: boolean;
    multiline?: boolean;
    onBlur?: (value: string) => void;
    onChange?: (value: string) => void;
    icon?: any;
    clearButton?: boolean;
    textFieldSx?: SxProps<Theme>;
};
export declare const RoundedInput: import("react").ForwardRefExoticComponent<Omit<RoundedInputProps, "ref"> & import("react").RefAttributes<HTMLDivElement>>;
