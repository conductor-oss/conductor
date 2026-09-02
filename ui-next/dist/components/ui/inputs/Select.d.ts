import { SelectProps as MuiSelectProps } from "@mui/material";
import { CSSProperties, ReactNode } from "react";
interface SelectProps extends Omit<MuiSelectProps, "renderValue"> {
    label?: ReactNode;
    fullWidth?: boolean;
    nullable?: boolean;
    style?: CSSProperties;
    renderValue?: (value: unknown) => ReactNode;
}
declare const Select: ({ label, fullWidth, nullable, style, ...props }: SelectProps) => import("react").JSX.Element;
export default Select;
