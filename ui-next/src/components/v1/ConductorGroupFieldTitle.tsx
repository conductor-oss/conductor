import { Grid } from "@mui/material";
import { ReactNode } from "react";

import { colors, fontSizes, fontWeights } from "theme/tokens/variables";

export const ConductorGroupFieldTitle = ({ title }: { title?: ReactNode }) => {
  return (
    <Grid
      container
      sx={{ width: "100%" }}
      alignItems="flex-start"
      spacing={4}
      marginBottom={1}
    >
      <Grid
        sx={{
          // Same as MuiInputLabel-shrink in the theme.
          fontWeight: fontWeights.fontWeight3,
          fontSize: fontSizes.fontSize2,
          paddingLeft: 0,
          marginBottom: ".3em",
          marginTop: 0,
          color: colors.black,
          opacity: 0.6,
        }}
        size={{
          sm: 12,
        }}
      >
        {title}
      </Grid>
    </Grid>
  );
};
