import { Box, Grid, useMediaQuery, useTheme } from "@mui/material";
import ConductorInput from "components/v1/ConductorInput";
import { ReactNode } from "react";
import { RefreshOptions } from "./refresher";

export interface QuickSearchProps {
  onChange: (val: string) => void;
  searchTerm: string;
  createButton?: ReactNode;
  description?: ReactNode;
  quickSearchPlaceholder: string;
  label?: ReactNode;
}

export const QuickSearchRefresh = ({
  label,
  quickSearchPlaceholder,
  searchTerm,
  onChange,
}: QuickSearchProps) => {
  const theme = useTheme();
  const mediumScreen = useMediaQuery(theme.breakpoints.up("md"));

  return (
    <Box pb={3} sx={{ height: "100%", padding: 6 }}>
      <Grid container spacing={2} sx={{ width: "100%" }}>
        <Grid size={{ xs: 12, md: 5 }}>
          <ConductorInput
            fullWidth={mediumScreen ? false : true}
            label={label}
            placeholder={quickSearchPlaceholder}
            showClearButton
            value={searchTerm}
            onTextInputChange={onChange}
            sx={{
              "& .MuiOutlinedInput-root": {
                minWidth: mediumScreen ? "30vw" : "200px",
              },
            }}
            autoFocus
          />
        </Grid>
        <Grid
          size={{ xs: 12, md: 7 }}
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-end",
          }}
        >
          <RefreshOptions />
        </Grid>
      </Grid>
    </Box>
  );
};
