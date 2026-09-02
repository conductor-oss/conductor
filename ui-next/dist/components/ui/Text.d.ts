import { type MuiTypographyProps } from "./MuiTypography";
export type TextLevel = 0 | 1 | 2;
type TextProps = Omit<MuiTypographyProps, "variant"> & {
    level?: TextLevel;
};
declare const Text: ({ level, sx, ...props }: TextProps) => import("react").JSX.Element;
export default Text;
