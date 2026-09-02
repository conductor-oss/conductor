import { BoxProps, GridProps } from "@mui/material";
import { FC, ReactNode } from "react";
export type ConductorGroupContainerProps = {
    Wrapper?: FC<BoxProps | GridProps>;
    children?: ReactNode;
};
export declare const ConductorGroupContainer: ({ Wrapper, children, }: ConductorGroupContainerProps) => import("react").JSX.Element;
