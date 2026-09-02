import React from "react";
interface EnforceSchemaProps {
    onChange: (value: boolean) => void;
    value?: boolean;
    defaultValue?: boolean;
    showEnforceSchemaSwitch?: boolean;
}
export declare const EnforceSchema: ({ onChange, value, defaultValue, showEnforceSchemaSwitch, }: EnforceSchemaProps) => React.JSX.Element;
export {};
