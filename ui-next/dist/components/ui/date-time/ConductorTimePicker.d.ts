import { SxProps } from "@mui/material";
import React from "react";
export interface ConductorTimePickerProps {
    id?: string;
    timeValue: string;
    label: string;
    sx?: SxProps;
    updateTime: (data: string) => void;
    error?: string;
}
export declare const ConductorTimePicker: ({ id, label, timeValue, sx, updateTime, error, ...restProps }: ConductorTimePickerProps) => React.JSX.Element;
