import { CSSProperties, ReactNode } from "react";
import MuiTypography from "./MuiTypography";

interface StrikedTextProps {
  children: ReactNode;
  sx?: CSSProperties;
}

const StrikedText = ({ children, sx, ...props }: StrikedTextProps) => {
  const customStyles = {
    textDecoration: "line-through",
    letterSpacing: "1px",
    ...sx,
  };

  return (
    <MuiTypography sx={customStyles} {...props}>
      {children}
    </MuiTypography>
  );
};

export default StrikedText;
