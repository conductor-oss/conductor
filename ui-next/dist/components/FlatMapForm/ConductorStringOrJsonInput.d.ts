import { ReactNode } from "react";
import type { ConductorAutocompleteVariablesProps } from "./ConductorAutocompleteVariables";
export type ConductorStringOrJsonInputProps = Omit<ConductorAutocompleteVariablesProps, "onChange" | "value"> & {
    value: string | Record<string, unknown>;
    onChange: (value: string | number | boolean | null) => void;
    helperText?: string;
    error?: boolean;
    customInput?: ReactNode;
    showFieldTypes: boolean;
    placeholder?: string;
};
export declare const ConductorStringOrJsonInput: ({ value, onChange, label, helperText, error, customInput, showFieldTypes, placeholder, }: ConductorStringOrJsonInputProps) => import("react").JSX.Element;
