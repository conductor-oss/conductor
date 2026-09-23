import { Box, Theme } from "@mui/material";
import { ReactNode } from "react";

import MuiTypography from "components/ui/MuiTypography";

const cardStyle = {
  backgroundColor: (theme: Theme) => theme.palette.background.paper,
  border: (theme: Theme) => `1px solid ${theme.palette.divider}`,
  borderRadius: 1,
};

// NB: this theme's spacing unit is 4px, not MUI's default 8px — every value
// here is half what the same number would mean in a stock MUI app.
const headerStyle = {
  display: "flex",
  alignItems: "center",
  gap: 3,
  minHeight: 40,
  px: 4,
  py: 1,
  borderBottom: (theme: Theme) => `1px solid ${theme.palette.divider}`,
};

/**
 * Compact but not quiet: small and tracked out so the header bar stays short,
 * full-strength colour and heavy weight so the section names still read as
 * structure rather than as captions.
 */
const titleStyle = {
  flex: 1,
  fontSize: 12,
  fontWeight: 600,
  letterSpacing: "0.08em",
  textTransform: "uppercase",
  color: "text.primary",
};

/**
 * A titled card. The form is a stack of these so that "what the handler is",
 * "what fires it", "when it fires" and "what it does" read as separate steps
 * instead of one undifferentiated column of fields.
 */
const FormSection = ({
  title,
  count,
  action,
  children,
  bodySx,
}: {
  title: string;
  /** Rendered next to the title, e.g. the number of actions. */
  count?: ReactNode;
  /** Right-aligned control in the header, e.g. an Add button. */
  action?: ReactNode;
  children: ReactNode;
  bodySx?: Record<string, unknown>;
}) => (
  <Box sx={cardStyle}>
    <Box sx={headerStyle}>
      <MuiTypography component="h2" sx={titleStyle}>
        {title}
        {count !== undefined && (
          <MuiTypography
            component="span"
            variant="inherit"
            sx={{ ml: 0.5, opacity: 0.7 }}
          >
            ({count})
          </MuiTypography>
        )}
      </MuiTypography>
      {action}
    </Box>
    <Box
      sx={{
        p: 4,
        display: "flex",
        flexDirection: "column",
        gap: 3,
        ...bodySx,
      }}
    >
      {children}
    </Box>
  </Box>
);

export default FormSection;
