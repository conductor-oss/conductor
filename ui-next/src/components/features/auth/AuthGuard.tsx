/**
 * Layout wrapper for OSS mode.
 *
 * In OSS mode, this is simply a layout container with no authentication checks.
 * Full auth guard logic has been moved to the enterprise package.
 */
import { Box } from "@mui/material";
import ErrorBoundary from "components/ErrorBoundary";
import { RunWorkflow } from "pages/runWorkflow";
import React from "react";
import { Outlet } from "react-router";

interface AuthGuardProps {
  fallback?: React.ReactNode;
  runWorkflow?: boolean;
}

const AuthGuard = ({
  fallback: _fallback = null,
  runWorkflow = false,
}: AuthGuardProps) => {
  return (
    <Box
      sx={{
        display: "flex",
        margin: 0,
        padding: 0,
        overflow: "auto",
        width: "100%",
        flexWrap: "nowrap",
        height: "100%",
        alignItems: "stretch",
      }}
    >
      {runWorkflow && <RunWorkflow />}
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          margin: 0,
          padding: 0,
          overflow: "auto",
          flexWrap: "nowrap",
          height: "100%",
          alignItems: "stretch",
          width: !runWorkflow ? "100%" : 0,
          visibility: !runWorkflow ? "visible" : "hidden",
          position: !runWorkflow ? "relative" : "absolute",
        }}
      >
        <ErrorBoundary>
          <Outlet />
        </ErrorBoundary>
      </Box>
    </Box>
  );
};

export default AuthGuard;
