import { TextFieldProps } from "@mui/material";
import { ChangeEvent, FunctionComponent } from "react";
type InputNumberProps = Omit<TextFieldProps, "onChange"> & {
    onChange: (val: number | null, event: ChangeEvent<HTMLInputElement>) => void;
};
/**
 * The requirement for this component was
 * "number" : null,
 *     "number" : 0,
 *    "number" : 10
 *  Meaning allow empty. and set to null if empty. no leading 0s
 * @param param0
 * @returns
 */
export declare const InputNumber: FunctionComponent<InputNumberProps>;
export {};
