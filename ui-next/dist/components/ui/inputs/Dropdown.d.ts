import { AutocompleteProps, CSSObject, TextFieldPropsSizeOverrides } from "@mui/material";
import { OverridableStringUnion } from "@mui/types";
import { ReactNode } from "react";
import { CustomInputProps } from "./Input";
type DropdownOption = string | number | {
    label: string;
};
type DropdownProps = Omit<AutocompleteProps<DropdownOption, boolean | undefined, boolean | undefined, boolean | undefined>, "renderInput" | "onInputChange" | "options"> & {
    onInputChange?: (value: string) => void;
    label?: ReactNode;
    style?: CSSObject;
    error?: boolean;
    size?: OverridableStringUnion<"small" | "medium", TextFieldPropsSizeOverrides>;
    helperText?: ReactNode;
    inputProps?: CustomInputProps;
    required?: boolean;
    options?: readonly DropdownOption[];
};
declare const Dropdown: import("react").ForwardRefExoticComponent<Omit<DropdownProps, "ref"> & import("react").RefAttributes<HTMLDivElement>>;
export default Dropdown;
