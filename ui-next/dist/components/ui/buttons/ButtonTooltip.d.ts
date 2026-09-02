import { FunctionComponent, ReactNode } from "react";
import { MuiButtonProps } from "./MuiButton";
export interface ButtonTooltipProps extends MuiButtonProps {
    tooltip: NonNullable<ReactNode>;
    variant?: "contained" | "text" | "outlined";
    disabled?: boolean;
    onClick: () => void;
    "data-testid"?: string;
    displayChildren?: boolean;
}
export declare const ButtonTooltip: FunctionComponent<ButtonTooltipProps>;
