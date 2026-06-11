import { Box, Switch, Typography } from "@mui/material";

interface SummarizeToggleProps {
  checked: boolean;
  onChange: (checked: boolean) => void;
}

export function SummarizeToggle({ checked, onChange }: SummarizeToggleProps) {
  return (
    <Box
      component="span"
      onClick={(e) => e.stopPropagation()}
      sx={{ display: "inline-flex", alignItems: "center", ml: 0.5, gap: 1 }}
    >
      <Typography
        component="span"
        sx={{ fontSize: 11, color: "text.secondary" }}
      >
        Summarize
      </Typography>
      <Switch
        size="small"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        sx={{ ml: -0.25 }}
      />
    </Box>
  );
}
