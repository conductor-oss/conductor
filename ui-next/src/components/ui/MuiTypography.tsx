import Typography, { TypographyProps } from "@mui/material/Typography";
import { CSSProperties, ElementType, FC } from "react";

interface MuiTypographyProps extends TypographyProps {
  style?: CSSProperties;
  opacity?: number;
  textDecoration?: "overline" | "line-through" | "underline";
  cursor?: string;
  component?: ElementType;
}

const MuiTypography: FC<MuiTypographyProps> = ({
  style,
  opacity,
  textDecoration,
  cursor,
  sx,
  ...props
}) => {
  const customStyles: CSSProperties = {
    ...style,
    opacity,
    textDecoration,
    cursor,
  };

  return <Typography {...props} sx={{ ...customStyles, ...sx }} />;
};

export default MuiTypography;
export type { MuiTypographyProps };
