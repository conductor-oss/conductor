import { Component, ErrorInfo, ReactNode } from "react";
import { Box } from "@mui/material";
// import { reportErrorToHeap, isHeapEnabled } from "utils";

import { reportErrorToLogRocket, isLogRocketEnabled } from "utils";
interface Props {
  children?: ReactNode;
  location?: any;
}

interface State {
  hasError: boolean;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
  };

  public static getDerivedStateFromError(_: Error): State {
    // Update state so the next render will show the fallback UI.
    return { hasError: true };
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
    if (prevProps?.location?.pathname !== this.props.location?.pathname) {
      this.setState({ hasError: false });
    }
  }

  public render() {
    if (this.state.hasError) {
      return (
        <Box
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
          <Box
            sx={{
              fontSize: "1rem",
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

export default ErrorBoundary;
