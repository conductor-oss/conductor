import { AutocompleteRenderOptionState, InputLabelProps } from "@mui/material";
import { SxProps } from "@mui/system";
import { ConductorInputProps } from "components/ui/inputs/ConductorInput";
import { FunctionComponent, HTMLAttributes, KeyboardEvent, ReactNode } from "react";
import { TaskDef } from "types/common";
import { ActorRef } from "xstate";
type CohercesToNumber = "integer" | "double";
type TypeCohersionNumber = {
    /** null when clearEmptyNumberAsNull and the field is cleared */
    onChange: (change: number | null) => void;
    coerceTo: CohercesToNumber;
};
type TypeCohersionString = {
    onChange: (change: string) => void;
    coerceTo?: "string";
};
type TypeCohersion = TypeCohersionNumber | TypeCohersionString;
export type ConductorAutocompleteVariablesProps = {
    label?: string | ReactNode;
    value?: string | number;
    fullWidth?: boolean;
    placeholder?: string;
    helperText?: string;
    taskBranches?: TaskDef[];
    workflowTasks?: TaskDef[];
    workflowInputParameters?: string[];
    actor?: ActorRef<any>;
    otherOptions?: string[] | number[];
    openOnFocus?: boolean;
    secrets?: string[];
    envs?: string[];
    InputLabelProps?: InputLabelProps;
    sxInput?: SxProps;
    onFocus?: () => void;
    growPopper?: boolean;
    workflowActor?: ActorRef<any>;
    onKeyDown?: (value: KeyboardEvent<HTMLInputElement>) => void;
    error?: boolean;
    id?: string;
    inputProps?: ConductorInputProps;
    required?: boolean;
    multiline?: boolean;
    variables?: string[];
    disabled?: boolean;
    onInputChange?: (val: any) => void;
    onBlur?: (val: string) => void;
    /**
     * When true, clearing a numeric field emits null instead of coercing "" → 0.
     * Opt-in — default preserves historical behavior for non-LLM forms.
     */
    clearEmptyNumberAsNull?: boolean;
    renderOption?: (props: HTMLAttributes<HTMLLIElement>, option: string | number, state: AutocompleteRenderOptionState) => ReactNode;
    getOptionLabel?: (option: string | number) => string;
} & TypeCohersion;
export declare const ConductorAutocompleteVariables: FunctionComponent<ConductorAutocompleteVariablesProps>;
export {};
