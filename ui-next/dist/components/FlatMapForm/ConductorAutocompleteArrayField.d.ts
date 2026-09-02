import { FunctionComponent, ReactNode } from "react";
import { ButtonPosition } from "utils/constants/common";
interface ConductorAutocompleteArrayFieldProps {
    value: any[];
    onChange: (val: any[]) => void;
    addButtonPosition?: ButtonPosition;
    isError?: boolean;
    hasAtLeastOne?: boolean;
    placeholder?: string;
    label?: ReactNode;
}
declare const AutocompleteArrayField: FunctionComponent<ConductorAutocompleteArrayFieldProps & {
    label?: string;
    taskType: import("../..").FormTaskType;
    path: string;
    onChange?: (val: any) => void;
    value?: any;
    onChangeHeaders?: (headers: any) => void;
}>;
export { AutocompleteArrayField };
