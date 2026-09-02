import { ChangeEvent } from "react";
export interface RadioButtonGroupProp {
    ariaLabel?: string;
    items: {
        disabled?: boolean;
        value: string | number;
        label: string;
        helperText?: string;
    }[];
    name: string;
    onChange?: (evt: ChangeEvent<HTMLInputElement>, val: string) => void;
    value?: string | number;
}
declare const RadioButtonGroup: ({ ariaLabel, items, name, onChange, value, }: RadioButtonGroupProp) => import("react").JSX.Element;
export default RadioButtonGroup;
