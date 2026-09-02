import { SxProps } from "@mui/material";
import { FunctionComponent } from "react";
import { ValueInputDefaultValues } from "utils";
interface ValueInputProps {
    onChangeValue: (a: string) => void;
    value: string | boolean;
    valueLabel?: string;
    defaultObjectValue?: ValueInputDefaultValues;
    dropDownOptions?: string[];
    keyStyle?: SxProps;
    valueStyle?: SxProps;
}
export declare const ConductorValueInput: FunctionComponent<ValueInputProps>;
export {};
