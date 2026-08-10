import React, { FunctionComponent } from "react";
import { Box, Switch } from "@mui/material";
import { Grid } from "@mui/system";
interface OptionalProps {
  onChange: (value: any) => void;
  taskJson: any;
}

export const Optional: FunctionComponent<OptionalProps> = ({
  onChange,
  taskJson,
}) => {
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    onChange({ ...taskJson, optional: e.target.checked });
  };
  return (
    <Grid container>
      <Grid>
        <Box sx={{ fontWeight: 600, color: "#767676", ml: -2.2 }}>
          <Switch
            color="primary"
            checked={taskJson.optional ?? false}
            onChange={handleChange}
          />
          Make Task Optional
        </Box>
        <Box style={{ opacity: 0.5 }}>
          The workflow continues unaffected by the task's outcome, whether it
          fails or remains incomplete.
        </Box>
      </Grid>
    </Grid>
  );
};
