import { SxProps } from "@mui/material";
import { DialogProps } from "@mui/material/Dialog";
import React, { CSSProperties, ReactNode } from "react";
type UIModalProps = Omit<DialogProps, "title"> & {
    style?: CSSProperties;
    setOpen: (value: boolean) => void;
    title?: string | React.ReactNode;
    description?: string | React.ReactNode;
    icon?: React.ReactNode;
    enableCloseButton?: boolean;
    backdropColor?: string;
    maxWidth?: any;
    footerChildren?: ReactNode;
    footerSx?: SxProps;
    titleSx?: SxProps;
};
declare const UIModal: React.ForwardRefExoticComponent<Omit<UIModalProps, "ref"> & React.RefAttributes<HTMLDivElement>>;
export default UIModal;
export type { UIModalProps };
