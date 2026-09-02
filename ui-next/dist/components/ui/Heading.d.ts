import { type MuiTypographyProps } from "./MuiTypography";
export type HeadingLevel = 0 | 1 | 2 | 3 | 4 | 5;
type HeadingProps = Omit<MuiTypographyProps, "variant"> & {
    level?: HeadingLevel;
};
declare const Heading: ({ level, ...props }: HeadingProps) => import("react").JSX.Element;
export default Heading;
