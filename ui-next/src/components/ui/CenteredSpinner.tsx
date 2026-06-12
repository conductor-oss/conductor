import { Box, CircularProgress, Typography } from "@mui/material";

interface CenteredSpinnerProps {
  message?: string;
}

const CenteredSpinner = ({ message }: CenteredSpinnerProps) => (
  <Box
    sx={{
      display: "flex",
      flexDirection: "column",
      alignItems: "center",
      justifyContent: "center",
      height: "100vh",
      width: "100%",
      gap: 2,
    }}
  >
    <CircularProgress size={40} />
    {message && (
      <Typography variant="body1" color="text.secondary">
        {message}
      </Typography>
    )}
  </Box>
);

export default CenteredSpinner;
