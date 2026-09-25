import { Component, ErrorInfo, ReactNode } from "react";
import { Box, Button } from "@mui/material";
import { useLocation } from "react-router";
// import { reportErrorToHeap, isHeapEnabled } from "utils";

import { reportErrorToLogRocket, isLogRocketEnabled } from "utils";
interface Props {
  children?: ReactNode;
  locationKey: string;
}

interface State {
  error: Error | null;
}

class ErrorBoundaryContent extends Component<Props, State> {
  public state: State = {
    error: null,
  };

  public static getDerivedStateFromError(error: Error): State {
    // Update state so the next render will show the fallback UI.
    return { error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught error:", error, errorInfo);

    // if (isHeapEnabled()) {
    //   reportErrorToHeap(error);
    // }
    if (isLogRocketEnabled()) {
      reportErrorToLogRocket(error);
    }
  }

  componentDidUpdate(prevProps: Props) {
    if (
      prevProps.locationKey !== this.props.locationKey &&
      this.state.error !== null
    ) {
      this.setState({ error: null });
    }
  }

  private handleRetry = () => this.setState({ error: null });

  public render() {
    if (this.state.error) {
      return (
        <Box
          role="alert"
          sx={{
            width: "100%",
            height: "100%",
            display: "flex",
            flexDirection: "column",
            justifyContent: "center",
            alignItems: "center",
          }}
        >
          <Box
            sx={{
              fontSize: "1.5rem",
            }}
          >
            There was an error performing this action. Please try again.
          </Box>
          <Box sx={{ fontSize: "1rem", mt: 1 }}>
            {this.state.error.message || "An unexpected error occurred."}
          </Box>
          <Button sx={{ mt: 2 }} onClick={this.handleRetry} variant="contained">
            Try again
          </Button>
          <Box
            sx={{
              fontSize: "1rem",
              mt: 2,
            }}
          >
            Contact support if the error persists.
          </Box>
        </Box>
      );
    }

    return this.props.children;
  }
}

/** Resets the boundary when the active route changes. */
export default function ErrorBoundary({ children }: { children?: ReactNode }) {
  const location = useLocation();
  const locationKey = `${location.pathname}${location.search}${location.hash}`;

  return (
    <ErrorBoundaryContent locationKey={locationKey}>
      {children}
    </ErrorBoundaryContent>
  );
}
