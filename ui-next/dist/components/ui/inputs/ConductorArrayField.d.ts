import { FunctionComponent, ReactNode } from "react";
import { ButtonPosition } from "utils/constants/common";
export interface ConductorArrayFieldProps {
    value: string[];
    onChange: (val: string[]) => void;
    isError?: boolean;
    placeholder?: string;
    customInput?: ReactNode;
    addButtonLabel?: string;
    inputLabel?: ReactNode;
    showType?: boolean;
    addButtonPosition?: ButtonPosition;
    disabledAddButton?: boolean;
    enableAutocomplete?: boolean;
    typeLabel?: ReactNode;
    helperText?: ReactNode;
}
declare const ConductorArrayFieldBase: FunctionComponent<ConductorArrayFieldProps>;
declare const ConductorArrayField: FunctionComponent<ConductorArrayFieldProps & {
    label?: string;
    taskType: import("../../..").FormTaskType;
    path: string;
    onChange?: (val: any) => void;
    value?: any;
    onChangeHeaders?: (headers: any) => void;
}>;
export { ConductorArrayField, ConductorArrayFieldBase };
