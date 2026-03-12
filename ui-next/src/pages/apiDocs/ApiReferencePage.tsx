/**
 * API Reference Page
 *
 * Redirects to the Swagger UI for API documentation.
 * This is a simple redirect component that opens the Swagger UI in the current window.
 */

import { useEffect } from "react";
import { Box, CircularProgress, Typography } from "@mui/material";

const getSwaggerUrl = () =>
  `//${window.location.host}/swagger-ui/index.html?configUrl=/api-docs/swagger-config#/`;

export default function ApiReferencePage() {
  useEffect(() => {
    // Redirect to Swagger UI
    window.location.href = getSwaggerUrl();
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
