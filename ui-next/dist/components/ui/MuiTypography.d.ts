import { TypographyProps } from "@mui/material/Typography";
import { CSSProperties, ElementType, FC } from "react";
interface MuiTypographyProps extends TypographyProps {
    style?: CSSProperties;
    opacity?: number;
    textDecoration?: "overline" | "line-through" | "underline";
    cursor?: string;
    component?: ElementType;
}
declare const MuiTypography: FC<MuiTypographyProps>;
export default MuiTypography;
export type { MuiTypographyProps };
