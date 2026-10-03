import { Box, Typography } from "@mui/material";
import { ReactNode } from "react";

/** The heading and help sentence shown above the picker's tabs. */
export const DatePickerPanelHeader = ({
  title,
  helpText,
  children,
}: {
  title: string;
  helpText: string;
  children: ReactNode;
}) => (
  // The theme spacing unit is 4px: 20px around the panel, 12px under the text.
  <Box sx={{ px: 5, pt: 5, pb: 4 }}>
    <Box sx={{ mb: 3 }}>
      <Typography variant="h6" sx={{ pb: 1, fontSize: "11pt" }}>
        {title}
      </Typography>
      <Typography>{helpText}</Typography>
    </Box>
    {children}
  </Box>
);
