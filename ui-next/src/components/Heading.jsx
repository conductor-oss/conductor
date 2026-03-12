import MuiTypography from "./MuiTypography";

const levelMap = ["h6", "h5", "h4", "h3", "h2", "h1"];

const Heading = ({ level = 3, ...props }) => {
  return <MuiTypography variant={levelMap[level]} {...props} />;
};

export default Heading;
