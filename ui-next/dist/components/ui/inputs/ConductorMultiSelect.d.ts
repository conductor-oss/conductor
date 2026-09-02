import { ReactNode } from "react";
type ConductorMultiSelectProp = {
    label: string;
    options: string[];
    onSelected: (val: string[]) => void;
    allText: string;
    value: string[];
    renderer?: (val: string) => ReactNode;
    dataTestId?: string;
    error?: boolean;
    helperText?: string;
};
export default function ConductorMultiSelect({ label, options, onSelected, allText, value, renderer, dataTestId, error, helperText, }: ConductorMultiSelectProp): import("react").JSX.Element;
export {};
