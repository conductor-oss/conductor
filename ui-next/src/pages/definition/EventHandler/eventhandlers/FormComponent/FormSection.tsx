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

// Sentence case at body-plus size, per the Event Handler design.
const titleStyle = {
  flex: 1,
  fontSize: 14,
  fontWeight: 500,
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
  id,
}: {
  title: string;
  /** DOM id for the card, so tests can scope queries to one section. */
  id?: string;
  /** Rendered next to the title, e.g. the number of actions. */
  count?: ReactNode;
  /** Right-aligned control in the header, e.g. an Add button. */
  action?: ReactNode;
  children: ReactNode;
  bodySx?: Record<string, unknown>;
}) => (
  <Box sx={cardStyle} id={id}>
    <Box sx={headerStyle}>
      <MuiTypography component="h2" sx={titleStyle}>
        {title}
        {count !== undefined && (
          <MuiTypography
            component="span"
            variant="inherit"
            sx={{
              ml: 2,
              fontFamily: "ui-monospace, Menlo, Monaco, Consolas, monospace",
              fontSize: 12,
              fontWeight: 400,
              color: "text.secondary",
            }}
          >
            {count}
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
