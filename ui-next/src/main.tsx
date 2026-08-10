import CssBaseline from "@mui/material/CssBaseline";
import { inspect } from "@xstate/inspect";
import { MessageProvider } from "components/providers/messageContext";
import "highlight.js/styles/agate.css";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HotkeysProvider } from "react-hotkeys-hook";
import { QueryClientProvider } from "react-query";
import { ReactQueryDevtools } from "react-query/devtools";
import { RouterProvider } from "react-router";
import { logger } from "utils";
import {
  HOT_KEYS_SIDEBAR,
  HOT_KEYS_WORKFLOW_DEFINITION,
} from "utils/constants/common";

// OSS build - no enterprise plugins are registered
// Enterprise builds import and register plugins in their own main.tsx

import { router } from "./routes/router";
import "./index.css";
import { queryClient } from "./queryClient";
import { Provider as ThemeProvider } from "./theme/material/provider";

if (import.meta.env.VITE_XSTATE_INSPECT === "true") {
  inspect({
    // options
    url: "https://stately.ai/viz?inspect=1", // (default)
    iframe: false, // open in new window
  });
}

logger.log("Monitoring disabled");

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("No root element found in index.html");
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <HotkeysProvider
      initiallyActiveScopes={[HOT_KEYS_SIDEBAR, HOT_KEYS_WORKFLOW_DEFINITION]}
    >
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <MessageProvider>
            <CssBaseline />
            <ReactQueryDevtools initialIsOpen={false} />
            <RouterProvider router={router} />
          </MessageProvider>
        </ThemeProvider>
      </QueryClientProvider>
    </HotkeysProvider>
  </StrictMode>,
);
