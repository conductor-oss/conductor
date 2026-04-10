import MuiTypography, { type MuiTypographyProps } from "./MuiTypography";

const levelMap = ["caption", "body2", "body1"] as const;

export type TextLevel = 0 | 1 | 2;

type TextProps = Omit<MuiTypographyProps, "variant"> & {
  level?: TextLevel;
};

const Text = ({ level = 1, sx, ...props }: TextProps) => {
  return <MuiTypography variant={levelMap[level]} {...props} sx={sx} />;
};

export default Text;
