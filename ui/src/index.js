import React from "react";
import ReactDOM from "react-dom";
import "./index.css";
import App from "./App";
import * as serviceWorker from "./serviceWorker";
import { Provider as ThemeProvider } from "./theme/provider";
import { BrowserRouter } from "react-router-dom";
import CssBaseline from "@material-ui/core/CssBaseline";
import { QueryClient, QueryClientProvider } from "react-query";
import { ReactQueryDevtools } from "react-query/devtools";
import { getBasename } from "./utils/helpers";
import { GoogleOAuthProvider } from "@react-oauth/google";
import AuthenticatedApp from "./AuthenticatedApp";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      cacheTime: 600000, // 10 mins
    },
  },
});

ReactDOM.render(
  //<React.StrictMode>
  <GoogleOAuthProvider clientId='905736008528-ovf2gb2pq175j2o8le60lptf0taqjkf7.apps.googleusercontent.com'>
  <QueryClientProvider client={queryClient}>
    <ThemeProvider>
      <BrowserRouter basename={getBasename()}>
        <CssBaseline />
        <ReactQueryDevtools initialIsOpen={true} />
        <AuthenticatedApp />
      </BrowserRouter>
    </ThemeProvider>
  </QueryClientProvider>
  </GoogleOAuthProvider>,
  //</React.StrictMode>
  document.getElementById("root")
);

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: https://bit.ly/CRA-PWA
serviceWorker.unregister();
