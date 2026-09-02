import { AutocompleteProps } from "@mui/material";
import { ConductorInputProps } from "./ConductorInput";
export type ConductorAutocompleteProps<T = string> = Omit<AutocompleteProps<T, boolean | undefined, boolean | undefined, boolean | undefined>, "renderInput"> & {
    label: string;
    placeholder?: string;
    error?: boolean;
    required?: boolean;
    helperText?: string;
    conductorInputProps?: Partial<ConductorInputProps>;
    id?: string;
    onTextInputChange?: (v: string) => void;
    dataTestId?: string;
};
export declare const ConductorAutoComplete: import("react").ForwardRefExoticComponent<Omit<ConductorAutocompleteProps<any>, "ref"> & import("react").RefAttributes<unknown>>;
