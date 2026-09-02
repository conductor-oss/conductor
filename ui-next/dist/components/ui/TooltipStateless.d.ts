import { TooltipProps } from "@mui/material";
import { ReactNode } from "react";
interface TooltipStatelessProps extends Omit<TooltipProps, "content"> {
    title: string;
    content: ReactNode;
    handleOpen: (value: boolean) => void;
    handleClose: () => void;
}
declare const TooltipStateless: ({ title, content, children, placement, open, handleOpen, handleClose, }: TooltipStatelessProps) => import("react").JSX.Element;
export type { TooltipStatelessProps };
export default TooltipStateless;
