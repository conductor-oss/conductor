import { Box, BoxProps, GridProps } from "@mui/material";
import { FC, ReactNode } from "react";

export type ConductorGroupContainerProps = {
  Wrapper?: FC<BoxProps | GridProps>;
  children?: ReactNode;
};

export const ConductorGroupContainer = ({
  Wrapper = Box,
  children,
}: ConductorGroupContainerProps) => {
  return <Wrapper>{children}</Wrapper>;
};
