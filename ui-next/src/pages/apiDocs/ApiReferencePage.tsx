import { useEffect } from "react";
import { useNavigate, useLocation } from "react-router";
import { Box, CircularProgress, Typography } from "@mui/material";

const getSwaggerUrl = () =>
  `//${window.location.host}/swagger-ui/index.html?configUrl=/api-docs/swagger-config#/`;

export default function ApiReferencePage() {
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    window.open(getSwaggerUrl(), "_blank", "noopener,noreferrer");
    // Go back to wherever the user was; fall back to home on a fresh load.
    if (location.key === "default") {
      navigate("/");
    } else {
      navigate(-1);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100vh",
        gap: 2,
      }}
    >
      <CircularProgress />
      <Typography variant="body1" color="text.secondary">
        Redirecting to API Documentation...
      </Typography>
    </Box>
  );
}
