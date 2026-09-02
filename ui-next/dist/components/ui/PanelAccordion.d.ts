import { AccordionProps, SxProps, Theme } from "@mui/material";
import { ReactNode } from "react";
export declare const PanelAccordion: ({ children, sx, title, defaultExpanded, ...rest }: {
    children: ReactNode;
    sx?: SxProps<Theme>;
    title: ReactNode;
    defaultExpanded?: boolean;
} & AccordionProps) => import("react").JSX.Element;
