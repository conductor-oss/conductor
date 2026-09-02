import { FunctionComponent, ReactNode } from "react";
interface ConductorAutoCompleteWithDescriptionProps {
    value?: string;
    options?: {
        name: string;
        description: ReactNode;
    }[];
    error?: boolean;
    helperText?: string;
    onChange: (value: string) => void;
    placeholder?: string;
    growPopper?: boolean;
    label?: ReactNode;
    disableClearable?: boolean;
}
export declare const ConductorAutoCompleteWithDescription: FunctionComponent<ConductorAutoCompleteWithDescriptionProps>;
export {};
