import { SxProps } from "@mui/material";
import { ConductorTooltipProps } from "components/ui/ConductorTooltip";
import { ReactNode } from "react";
export interface ConductorDateRangePickerProps {
    disabled?: boolean;
    error?: boolean;
    from: Date | null;
    helperTextFrom?: ReactNode;
    helperTextTo?: ReactNode;
    inputSx?: SxProps;
    labelFrom?: string;
    labelTo?: string;
    onFromChange: (val: any) => void;
    onToChange: (val: any) => void;
    sx?: SxProps;
    to: Date | null;
    tooltipTo?: Omit<ConductorTooltipProps, "children">;
    tooltipFrom?: Omit<ConductorTooltipProps, "children">;
}
declare const ConductorDateRangePicker: ({ disabled, error, from, helperTextFrom, helperTextTo, inputSx, labelFrom, labelTo, onFromChange, onToChange, sx, to, tooltipTo, tooltipFrom, }: ConductorDateRangePickerProps) => import("react").JSX.Element;
export default ConductorDateRangePicker;
