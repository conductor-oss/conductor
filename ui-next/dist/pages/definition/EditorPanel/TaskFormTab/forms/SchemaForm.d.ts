import { FunctionComponent } from "react";
export interface SchemaFormValue {
    name: string;
    version?: number;
    type?: string;
}
export interface SchemaFormPropsValue {
    inputSchema?: SchemaFormValue;
    outputSchema?: SchemaFormValue;
    enforceSchema?: boolean;
}
export interface SchemaFormProps {
    value?: SchemaFormPropsValue;
    onChange: (value?: SchemaFormPropsValue) => void;
    hideOutputSchema?: boolean;
    hideInputSchema?: boolean;
    hideEnforceSchema?: boolean;
}
export declare const SchemaForm: FunctionComponent<SchemaFormProps>;
