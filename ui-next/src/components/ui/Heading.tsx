import MuiTypography, { type MuiTypographyProps } from "./MuiTypography";

const levelMap = ["h6", "h5", "h4", "h3", "h2", "h1"] as const;

export type HeadingLevel = 0 | 1 | 2 | 3 | 4 | 5;

type HeadingProps = Omit<MuiTypographyProps, "variant"> & {
  level?: HeadingLevel;
};

const Heading = ({ level = 3, ...props }: HeadingProps) => {
  return <MuiTypography variant={levelMap[level]} {...props} />;
};

export default Heading;
