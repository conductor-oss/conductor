import { FunctionComponent, ReactNode } from "react";
interface ConductorStringArrayFormFieldProps {
    inputParameters: string[];
    onChange: (newInputParams: string[]) => void;
    someKey?: string;
    addButtonLabel?: ReactNode;
    label?: ReactNode;
    title?: ReactNode;
    compact?: boolean;
    emptyListMessage?: ReactNode;
}
export declare const ConductorStringArrayFormField: FunctionComponent<ConductorStringArrayFormFieldProps>;
export {};
