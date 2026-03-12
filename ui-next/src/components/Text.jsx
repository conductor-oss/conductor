import MuiTypography from "./MuiTypography";

const levelMap = ["caption", "body2", "body1"];

const Text = ({ level = 1, sx, ...props }) => {
  return <MuiTypography variant={levelMap[level]} style={sx} {...props} />;
};

export default Text;
