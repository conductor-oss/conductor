import { Box, FormControlLabel, Switch } from "@mui/material";

interface SwitchComponentProps {
  asQuery: boolean;
  onToggle: () => void;
}

export const SwitchComponent = ({
  asQuery,
  onToggle,
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
        control={<Switch color="primary" onChange={onToggle} />}
        label="SQL format"
      />
    </Box>
  );
};
