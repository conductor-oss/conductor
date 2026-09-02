import { TextFieldProps } from "@mui/material/TextField";
export type CustomInputProps = Omit<TextFieldProps, "onBlur" | "onChange"> & {
    clearable?: boolean;
    onBlur?: (value: string) => void;
    onChange?: (value: string) => void;
};
declare const CustomInput: import("react").ForwardRefExoticComponent<Omit<CustomInputProps, "ref"> & import("react").RefAttributes<unknown>>;
export default CustomInput;
