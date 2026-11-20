import { Theme } from "@mui/material";
import MuiTypography, { MuiTypographyProps } from "components/ui/MuiTypography";
import { colors } from "theme/tokens/variables";

export type HelperTextProps = MuiTypographyProps;

/**
 * Secondary text under a field. Rendered as a div so callers can pass inline
 * markup without risking invalid nesting inside Typography's default <p>.
 *
 * Sized like MUI's FormHelperText (12px). Not `variant="body2"`: this theme's
 * body2 is 14px, larger than the field text it sits under. Coloured with the
 * design system's `--text-muted` (blackLight, 70%) rather than
 * `text.secondary`, which is 40% here and too faint to read comfortably.
 */
const HelperText = ({ children, sx, ...props }: HelperTextProps) => (
  <MuiTypography
    component="div"
    // Object merge, not MUI's array form: MuiTypography spreads `sx` into an
    // object itself, which would mangle an array.
    sx={{
      pt: 2,
      fontSize: 12,
      fontWeight: 300,
      lineHeight: 1.5,
      color: (theme: Theme) =>
        theme.palette.mode === "dark" ? colors.gray12 : colors.blackLight,
      ...sx,
    }}
    {...props}
  >
    {children}
  </MuiTypography>
);

export default HelperText;
