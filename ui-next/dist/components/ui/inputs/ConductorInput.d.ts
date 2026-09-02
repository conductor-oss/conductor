import { TextFieldProps, Theme } from "@mui/material";
import { ConductorTooltipProps } from "components/ui/ConductorTooltip";
import { ReactNode } from "react";
export type ConductorInputStyleProps = {
    theme: Theme;
    isFocused?: boolean;
    error?: boolean;
    multiline?: boolean;
    disabled?: boolean;
    isLabel?: boolean;
    isInputEmpty?: boolean;
};
export declare const MaybeTooltipLabel: ({ tooltip, label, required, }: {
    tooltip?: Omit<ConductorTooltipProps, "children">;
    label: ReactNode;
    required?: boolean;
}) => import("react").JSX.Element;
type ConductorInputProps = Omit<TextFieldProps, "ref"> & {
    onTextInputChange?: (value: string) => void;
    isSecret?: boolean;
    showClearButton?: boolean;
    tooltip?: Omit<ConductorTooltipProps, "children">;
};
declare const ConductorInput: import("react").ForwardRefExoticComponent<Omit<TextFieldProps, "ref"> & {
    onTextInputChange?: (value: string) => void;
    isSecret?: boolean;
    showClearButton?: boolean;
    tooltip?: Omit<ConductorTooltipProps, "children">;
} & import("react").RefAttributes<HTMLDivElement>>;
export type { ConductorInputProps };
export default ConductorInput;
