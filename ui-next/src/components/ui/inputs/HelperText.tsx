import MuiTypography, { MuiTypographyProps } from "components/ui/MuiTypography";

export type HelperTextProps = MuiTypographyProps;

/**
 * Secondary text under a field. Rendered as a div so callers can pass inline
 * markup without risking invalid nesting inside Typography's default <p>.
 */
const HelperText = ({ children, ...props }: HelperTextProps) => (
  <MuiTypography variant="body2" component="div" opacity={0.5} {...props}>
    {children}
  </MuiTypography>
);

export default HelperText;
