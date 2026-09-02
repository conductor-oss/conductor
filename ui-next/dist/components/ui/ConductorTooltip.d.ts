import { TooltipProps } from "@mui/material";
import { ReactNode } from "react";
interface ConductorTooltipProps extends Omit<TooltipProps, "content"> {
    title: string;
    content: ReactNode;
    showInitial?: boolean;
    initialTimeout?: number;
    onClose?: () => void;
}
declare function ConductorTooltip({ title, content, children, placement, showInitial, initialTimeout, onClose, }: ConductorTooltipProps): import("react").JSX.Element;
export default ConductorTooltip;
export type { ConductorTooltipProps };
