import { FunctionComponent } from "react";
import { ValueInputDefaultValues } from "utils";
interface ValueInputProps {
    onChangeValue: (a: string) => void;
    value: string | object;
    valueLabel?: string;
    defaultObjectValue?: ValueInputDefaultValues;
    dropDownOptions?: string[];
}
export declare const ConductorObjectOrStringInput: FunctionComponent<ValueInputProps>;
export {};
