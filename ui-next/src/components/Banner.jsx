import { Paper } from "@mui/material";

export default function Banner({ children, ...rest }) {
  return (
    <Paper
      elevation={0}
      classes={{
        root: {
          padding: 0,
          backgroundColor: "var(--backgroundLightest)",
          color: "rgba(0, 0, 0, 0.9)",
          borderLeft: "solid var(--backgroundLightest) 4px",
        },
      }}
      {...rest}
    >
      {children}
    </Paper>
  );
}
