import { ReactNode } from "react";
import { ConductorInputProps } from "./ConductorInput";
export type SelectItemType = {
    label: string;
    value: string | number;
} | string | number;
export type ConductorSelectProps = ConductorInputProps & {
    items?: SelectItemType[];
    children?: ReactNode;
};
declare const ConductorSelect: ({ items, children, ...props }: ConductorSelectProps) => import("react").JSX.Element;
export type HeadBarSelectProps = {
    items?: SelectItemType[];
    children?: ReactNode;
    value?: string | number;
    onChange?: (value: string) => void;
    label?: string;
    fullWidth?: boolean;
    labelOnEmpty?: string;
};
declare const HeadBarSelect: ({ items, children, value, onChange, label, fullWidth, labelOnEmpty, }: HeadBarSelectProps) => import("react").JSX.Element;
export { ConductorSelect, HeadBarSelect };
export default ConductorSelect;
