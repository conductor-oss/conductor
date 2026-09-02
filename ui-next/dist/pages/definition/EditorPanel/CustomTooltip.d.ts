import React from "react";
import { PopperPlacementType } from "@mui/material";
interface CustomTooltipProps {
    open: boolean;
    anchorEl: HTMLElement | null;
    onClose: () => void;
    content: React.ReactNode;
    placement?: PopperPlacementType;
    maxWidth?: number;
}
declare const CustomTooltip: React.FC<CustomTooltipProps>;
export default CustomTooltip;
