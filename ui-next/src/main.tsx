import CssBaseline from "@mui/material/CssBaseline";
import { inspect } from "@xstate/inspect";
import { loader } from "@monaco-editor/react";
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

import packageJson from "../package.json";
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

// @monaco-editor/loader defaults to a hardcoded CDN version (independent of
// our package.json) unless explicitly configured. Point it at the version we
// actually depend on, read straight from package.json, so bumping the
// `monaco-editor` dependency there is enough to change what loads at runtime.
// (We can't read monaco-editor's own package.json directly - its `exports`
// map blocks any subpath other than the ones it explicitly lists.)
const monacoEditorVersionRange =
  packageJson.devDependencies["monaco-editor"] ??
  packageJson.peerDependencies["monaco-editor"];
const monacoEditorCdnVersion = monacoEditorVersionRange.replace(/^[^\d]*/, "");
loader.config({
  paths: {
    vs: `https://cdn.jsdelivr.net/npm/monaco-editor@${monacoEditorCdnVersion}/min/vs`,
  },
});

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
