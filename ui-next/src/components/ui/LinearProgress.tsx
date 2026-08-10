import { default as MuiLinearProgress } from "@mui/material/LinearProgress";

export default function LinearProgress({ sx = {}, ...props }) {
  return (
    <MuiLinearProgress
      sx={[
        {
          marginBottom: "-4px",
          zIndex: 999,
        },
        sx,
      ]}
      {...props}
    />
  );
}
