import { CSSProperties, ReactNode } from "react";
import { SxProps } from "@mui/material";
export interface ClipboardCopyProps {
    children?: ReactNode;
    value: string;
    buttonId?: string;
    sx?: SxProps;
    linkStyle?: CSSProperties;
    iconPlacement?: "start" | "end";
}
export default function ClipboardCopy({ children, value, buttonId, sx, linkStyle, iconPlacement, }: ClipboardCopyProps): import("react").JSX.Element;
