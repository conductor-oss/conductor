import React from "react";
import MuiTypography from "./MuiTypography";
import { colors } from "theme/tokens/variables";
import { openInNewTab } from "utils/helpers";
import DocsIcon from "components/icons/DocsIcon";

interface DocLinkProps {
  url: string;
  label: string;
  position?: "relative" | "absolute";
  right?: string;
  top?: string;
}

export const DocLink = ({
  url,
  label,
  position = "absolute",
  right = "20px",
  top = "5px",
}: DocLinkProps) => {
  return (
    <MuiTypography
      position={position}
      right={right}
      top={top}
      display="flex"
      alignItems="center"
      gap={1}
      fontSize={14}
      color={colors.blueLightMode}
      fontWeight="bold"
      cursor="pointer"
      onClick={() => openInNewTab(url)}
    >
      <DocsIcon /> {label}
    </MuiTypography>
  );
};
