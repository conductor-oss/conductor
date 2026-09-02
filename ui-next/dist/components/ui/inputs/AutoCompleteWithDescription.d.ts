import { FunctionComponent, ReactNode } from "react";
interface AutoCompleteWithDescriptionProps {
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
}
export declare const AutoCompleteWithDescription: FunctionComponent<AutoCompleteWithDescriptionProps>;
export {};
