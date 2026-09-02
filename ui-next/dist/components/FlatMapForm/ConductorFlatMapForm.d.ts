import { FunctionComponent, ReactNode } from "react";
export interface ConductorFlatMapFormProps {
    title?: string | null;
    addItemLabel?: string;
    keyColumnLabel?: string;
    valueColumnLabel?: string;
    typeColumnLabel?: string;
    hideValue?: boolean;
    hideButtons?: boolean;
    showFieldTypes?: boolean;
    value?: Record<string, string>;
    onChange?: (newValues: Record<string, string>) => void;
    hiddenKeys?: string[];
    someKey?: string;
    enableAutocomplete?: boolean;
    autoFocusField?: boolean;
    customInput?: ReactNode;
    keyGenFunction?: () => string;
    valGenFunction?: () => string;
    focusOnField?: string;
    isSwitchCase?: boolean;
    placeholder?: string;
    compact?: boolean;
    emptyListMessage?: ReactNode;
    otherOptions?: string[];
}
declare const ConductorFlatMapFormBase: FunctionComponent<ConductorFlatMapFormProps>;
declare const ConductorFlatMapForm: FunctionComponent<ConductorFlatMapFormProps & {
    label?: string;
    taskType: import("../..").FormTaskType;
    path: string;
    onChange?: (val: any) => void;
    value?: any;
    onChangeHeaders?: (headers: any) => void;
}>;
export { ConductorFlatMapForm, ConductorFlatMapFormBase };
