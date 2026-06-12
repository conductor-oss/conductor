import { Box, FormControlLabel, Switch } from "@mui/material";
import { SetStateAction } from "react";
import { QueryDispatch } from "react-router-use-location-state";

interface SwitchComponentProps {
  asQuery: boolean;
  setAsQuery: QueryDispatch<SetStateAction<boolean>>;
}

export const SwitchComponent = ({
  asQuery,
  setAsQuery,
}: SwitchComponentProps) => {
  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: "flex-end",
        px: 3,
        pt: 2,
      }}
    >
      <FormControlLabel
        checked={asQuery}
        control={
          <Switch color="primary" onChange={() => setAsQuery(!asQuery)} />
        }
        label="SQL format"
      />
    </Box>
  );
};
